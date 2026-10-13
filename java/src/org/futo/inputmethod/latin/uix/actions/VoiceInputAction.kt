package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ANIMATE_BUBBLE
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.CAN_EXPAND_SPACE
import org.futo.inputmethod.latin.uix.CloseResult
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.USE_PERSONAL_DICT
import org.futo.inputmethod.latin.uix.USE_VAD_AUTOSTOP
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.uix.VOICE_AUTO_CAPITALIZE
import org.futo.inputmethod.latin.uix.VOICE_CONTEXT_PROMPT
import org.futo.inputmethod.latin.uix.VOICE_DEBUG_LOG
import org.futo.inputmethod.latin.uix.VOICE_FILENAME_RECOGNITION
import org.futo.inputmethod.latin.uix.VOICE_FORMAT_LISTS
import org.futo.inputmethod.latin.uix.VOICE_HINGLISH
import org.futo.inputmethod.latin.uix.VOICE_POST_PROCESSING
import org.futo.inputmethod.latin.uix.VOICE_REMOVE_FILLERS
import org.futo.inputmethod.latin.uix.VOICE_REMOVE_VERBAL_TICS
import org.futo.inputmethod.latin.uix.VOICE_SMART_PARAGRAPHS
import org.futo.inputmethod.latin.uix.VOICE_SPOKEN_PUNCTUATION
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.utils.ModelOutputSanitizer
import org.futo.inputmethod.latin.uix.utils.VoiceDictationLog
import org.futo.inputmethod.latin.uix.utils.VoicePostProcessor
import org.futo.inputmethod.latin.xlm.UserDictionaryObserver
import org.futo.inputmethod.updates.openURI
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.util.Locale

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.action_system_voice_input_title,
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null,
    shownInEditor = false
)


@Composable
fun NoModelInstalled(locale: Locale) {
    val context = LocalContext.current
    Box(modifier = Modifier
        .fillMaxSize()
        .clickable(
            enabled = true,
            onClickLabel = null,
            onClick = {
                context.openURI("https://keyboard.futo.tech/voice-input-models", true)
            },
            role = null,
            indication = null,
            interactionSource = remember { MutableInteractionSource() })) {
        Text(
            stringResource(
                R.string.action_voice_input_no_model_for_language_x_installed,
                locale.getDisplayName(locale)
            ), modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp), textAlign = TextAlign.Center)
    }
}

class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    val modelManager = ModelManager(manager.getContext())
    val soundPlayer = SoundPlayer(manager.getContext())
    val userDictionaryObserver = UserDictionaryObserver(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }

    override fun close() {
        runBlocking { modelManager.cleanUp() }
        userDictionaryObserver.unregister()
    }
}

private class VoiceInputActionWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState,
    val model: ModelLoader, val locales: List<Locale>,
    /** [model] is the Hinglish model imported for Hindi (see [hinglishModelFor]). */
    val hinglishModel: Boolean = false
) : ActionWindow(), RecognizerViewListener {
    val context = manager.getContext()

    // Created before initJob launches: loadSettings() reads its text context on another thread.
    private var inputTransaction = manager.createInputTransaction()

    /** Personal-dictionary words, also used by the post-processor to fix names and casing. */
    @Volatile private var vocabulary: List<String> = emptyList()

    private var shouldPlaySounds: Boolean = false
    private fun loadSettings(): RecognizerViewSettings {
        val enableSound = context.getSetting(ENABLE_SOUND)
        val verboseFeedback = false//context.getSetting(VERBOSE_PROGRESS)
        val disallowSymbols = context.getSetting(DISALLOW_SYMBOLS)
        val useBluetoothAudio = context.getSetting(PREFER_BLUETOOTH)
        val requestAudioFocus = context.getSetting(AUDIO_FOCUS)
        val canExpandSpace = context.getSetting(CAN_EXPAND_SPACE)
        val useVAD = context.getSetting(USE_VAD_AUTOSTOP)
        val usePersonalDict = context.getSetting(USE_PERSONAL_DICT)
        val animateBubble = context.getSetting(ANIMATE_BUBBLE)

        val primaryModel = model
        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val glossary = if(usePersonalDict) {
            state.userDictionaryObserver.getWords(locales).filter { it.shortcut.isNullOrEmpty() }.map { it.word }
        } else {
            emptyList()
        }
        vocabulary = glossary

        // Hinglish models (e.g. Oriserve Whisper-Hindi2Hinglish) write Latin-script Hinglish only when
        // decoded as "en" (forcing "hi" falls back to Devanagari), are corrupted by English prompts,
        // and are not ACFT-finetuned, so they need the full audio context.
        val allowedLanguages = if (hinglishModel) setOf(Language.English)
            else locales.mapNotNull { getLanguageFromWhisperString(it.language) }.toSet()
        val promptGlossary = if (hinglishModel) emptyList() else glossary
        val promptContext = if (!hinglishModel && context.getSetting(VOICE_CONTEXT_PROMPT)) {
            inputTransaction.textContext.promptContext?.toString() ?: ""
        } else ""

        shouldPlaySounds = enableSound

        return RecognizerViewSettings(
            shouldShowInlinePartialResult = false,
            shouldShowVerboseFeedback = verboseFeedback,
            shouldAnimateBubble = animateBubble,
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel,
                languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                glossary = promptGlossary,
                languages = allowedLanguages,
                suppressSymbols = disallowSymbols,
                context = promptContext,
                fullAudioContext = hinglishModel
            ),
            recordingConfiguration = RecordingSettings(
                preferBluetoothMic = useBluetoothAudio,
                requestAudioFocus = requestAudioFocus,
                canExpandSpace = canExpandSpace,
                useVADAutoStop = useVAD
            )
        )
    }

    private var recognizerView: MutableState<RecognizerView?> = mutableStateOf(null)
    private var modelException: MutableState<ModelDoesNotExistException?> = mutableStateOf(null)

    private val initJob = manager.getLifecycleScope().launch(Dispatchers.Default) {
        yield()
        val settings = loadSettings()

        yield()
        val recognizerView = try {
            RecognizerView(
                context = manager.getContext(),
                listener = this@VoiceInputActionWindow,
                settings = settings,
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )
        } catch(e: ModelDoesNotExistException) {
            modelException.value = e
            return@launch
        }

        this@VoiceInputActionWindow.recognizerView.value = recognizerView

        //yield()
        recognizerView.reset()

        //yield()
        recognizerView.start()
    }

    @Composable
    private fun ModelDownloader(modelException: ModelDoesNotExistException) {
        NoModelInstalled(locales.firstOrNull() ?: Locale.ROOT)
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.action_voice_input_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = true,
                onClickLabel = null,
                onClick = { recognizerView.value?.finish() },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })
            .semantics(mergeDescendants = true) {
                traversalIndex = -1.0f
            }) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                when {
                    modelException.value != null -> ModelDownloader(modelException.value!!)
                    recognizerView.value != null -> recognizerView.value!!.Content()
                }
            }
        }
    }

    override fun close(): CloseResult {
        inputTransaction.cancel()
        runBlocking { initJob.cancelAndJoin() }
        recognizerView.value?.cancel()
        state.modelManager.cancelAll()
        return CloseResult.Default
    }

    private var wasFinished = false
    private var cancelPlayed = false
    override fun cancelled() {
        if (!wasFinished) {
            if (shouldPlaySounds && !cancelPlayed) {
                state.soundPlayer.playCancelSound()
                cancelPlayed = true
            }
            inputTransaction.cancel()
        }
    }

    override fun recordingStarted(device: MicrophoneDeviceState) {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()
        }

        // Only set the setting if bluetooth is available, else it would reset the setting
        // every time it's used without a bluetooth device connected.
        if(device.bluetoothAvailable) {
            manager.getLifecycleScope().launch {
                context.setSetting(PREFER_BLUETOOTH, device.bluetoothActive)
            }
        }
    }

    private fun postProcess(result: String, partial: Boolean): String {
        if (!context.getSetting(VOICE_POST_PROCESSING)) return result
        return VoicePostProcessor.process(
            raw = result,
            removeFillers       = context.getSetting(VOICE_REMOVE_FILLERS),
            removeVerbalTics    = context.getSetting(VOICE_REMOVE_VERBAL_TICS),
            spokenPunctuation   = context.getSetting(VOICE_SPOKEN_PUNCTUATION),
            autoCapitalize      = context.getSetting(VOICE_AUTO_CAPITALIZE),
            numberWords         = true,
            filenameRecognition = context.getSetting(VOICE_FILENAME_RECOGNITION),
            smartParagraphs     = context.getSetting(VOICE_SMART_PARAGRAPHS),
            formatLists         = context.getSetting(VOICE_FORMAT_LISTS),
            locale              = RichInputMethodManager.getInstance().currentSubtypeLocale,
            vocabulary          = vocabulary,
            hinglish            = context.getSetting(VOICE_HINGLISH),
            partial             = partial
        )
    }

    override fun finished(result: String) {
        wasFinished = true

        manager.getLifecycleScope().launch(Dispatchers.Main) {
            val postProcessed = postProcess(result, partial = false)
            val sanitized = ModelOutputSanitizer.sanitize(postProcessed, inputTransaction.textContext)
            inputTransaction.commit(sanitized)
            manager.announce(postProcessed)
            manager.closeActionWindow()

            if (context.getSetting(VOICE_DEBUG_LOG)) {
                val locale = RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag()
                launch(Dispatchers.IO) { VoiceDictationLog.append(context, locale, result, postProcessed) }
            }
        }
    }

    override fun partialResult(result: String) {
        manager.getLifecycleScope().launch(Dispatchers.Main) {
            val postProcessed = postProcess(result, partial = true)
            val sanitized = ModelOutputSanitizer.sanitize(postProcessed, inputTransaction.textContext)
            inputTransaction.updatePartial(sanitized)
        }
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        return false
    }

    override fun openSettings() {
        SettingsActivity.openToNavDest(context, "languages")
    }
}

private class VoiceInputNoModelWindow(val locale: Locale) : ActionWindow() {
    @Composable
    override fun windowName(): String {
        return stringResource(R.string.action_voice_input_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        NoModelInstalled(locale)
    }
}

val VoiceInputAction = Action(icon = R.drawable.mic_fill,
    name = R.string.action_voice_input_title,
    simplePressImpl = null,
    keepScreenAwake = true,
    persistentState = { VoiceInputPersistentState(it) },
    windowImpl = { manager, persistentState ->
        val locales = manager.getActiveLocales()

        val hinglish = hinglishModelFor(manager.getContext())
        val model = hinglish
            ?: ResourceHelper.tryFindingVoiceInputModelForLocale(manager.getContext(), locales.firstOrNull() ?: Locale.ROOT)

        if(model == null) {
            VoiceInputNoModelWindow(locales.firstOrNull() ?: Locale.ROOT)
        } else {
            VoiceInputActionWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locales = locales, model = model, hinglishModel = hinglish != null
            )
        }
    }
)

/**
 * In Hinglish mode, the model imported for Hindi is used for every dictation, whatever keyboard
 * layout is active, so a sentence can switch between Hindi and English freely.
 */
private fun hinglishModelFor(context: Context): ModelLoader? {
    if (!context.getSetting(VOICE_HINGLISH)) return null
    // Only an imported file counts; never fall back to a built-in English model.
    ResourceHelper.findFileForKind(context, Locale("hi"), FileKind.VoiceInput) ?: return null
    return ResourceHelper.tryFindingVoiceInputModelForLocale(context, Locale("hi"))
}
