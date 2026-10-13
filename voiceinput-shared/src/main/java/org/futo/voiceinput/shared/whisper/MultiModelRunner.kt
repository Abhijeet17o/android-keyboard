package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader,
    val languageSpecificModels: Map<Language, ModelLoader>
)

data class DecodingConfiguration(
    val glossary: List<String>,
    val languages: Set<Language>,
    val suppressSymbols: Boolean,
    /** Text already in the field before the cursor; Whisper continues its vocabulary and style. */
    val context: String = "",
    /** Run the encoder over the full 30 s window; needed for models that are not ACFT-finetuned. */
    val fullAudioContext: Boolean = false
)

// Whisper keeps only the last ~224 prompt tokens (n_text_ctx / 2) and silently drops prompts over
// 1024 tokens, so both parts are capped (~4 characters per token). The context goes last because
// Whisper keeps the end of the prompt and the most recent text matters most.
private const val MAX_GLOSSARY_CHARS = 480
private const val MAX_CONTEXT_CHARS = 280

internal fun buildPrompt(glossary: List<String>, context: String): String {
    val parts = ArrayList<String>(2)
    if (glossary.isNotEmpty()) {
        val sb = StringBuilder()
        for (word in glossary) {
            if (sb.length + word.length + 2 > MAX_GLOSSARY_CHARS) break
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(word)
        }
        if (sb.isNotEmpty()) parts.add("(Glossary: $sb)")
    }
    val tail = context.trim().let { c ->
        if (c.length <= MAX_CONTEXT_CHARS) c
        else c.takeLast(MAX_CONTEXT_CHARS).let { cut -> cut.substring(cut.indexOf(' ') + 1) }
    }
    if (tail.isNotEmpty()) parts.add(tail)
    return parts.joinToString("\n")
}

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.forEach { it.join() }
    }

    @Throws(InferenceCancelledException::class)
    suspend fun run(
        samples: FloatArray,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.LoadingModel)
        val primaryModel = modelManager.obtainModel(runConfiguration.primaryModel)

        val allowedLanguages = decodingConfiguration.languages.map { it.toWhisperString() }.toTypedArray()
        val bailLanguages = runConfiguration.languageSpecificModels.filter { it.value != runConfiguration.primaryModel }.keys.map { it.toWhisperString() }.toTypedArray()

        val glossary = buildPrompt(decodingConfiguration.glossary, decodingConfiguration.context)

        val result = try {
            callback.updateStatus(InferenceState.Encoding)
            primaryModel.infer(
                samples = samples,
                prompt = glossary,
                languages = allowedLanguages,
                bailLanguages = bailLanguages,
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = decodingConfiguration.suppressSymbols,
                partialResultCallback = {
                    callback.partialResult(it)
                },
                fullAudioContext = decodingConfiguration.fullAudioContext
            )
        } catch(e: BailLanguageException) {
            callback.updateStatus(InferenceState.SwitchingModel)
            val language = getLanguageFromWhisperString(e.language)

            val specificModelLoader = runConfiguration.languageSpecificModels[language]!!
            val specificModel = modelManager.obtainModel(specificModelLoader)

            specificModel.infer(
                samples = samples,
                prompt = glossary,
                languages = arrayOf(e.language),
                bailLanguages = arrayOf(),
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = decodingConfiguration.suppressSymbols,
                partialResultCallback = {
                    callback.partialResult(it)
                },
                fullAudioContext = decodingConfiguration.fullAudioContext
            )
        }

        return@coroutineScope result
    }

    fun cancelAll() {
        modelManager.cancelAll()
    }
}