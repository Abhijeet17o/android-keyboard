package org.futo.inputmethod.latin.uix.utils.voicepp

import java.util.Locale

data class VoicePPOptions(
    val removeFillers: Boolean = true,
    val removeVerbalTics: Boolean = false,
    val spokenPunctuation: Boolean = true,
    val autoCapitalize: Boolean = true,
    val numberWords: Boolean = true,
    val filenameRecognition: Boolean = true,
    val smartParagraphs: Boolean = false,
    val formatLists: Boolean = true,
    val locale: Locale = Locale.US,
    /** The user's personal dictionary: names, brands, jargon. */
    val vocabulary: List<String> = emptyList(),
    /** Hinglish: write Devanagari in Latin script and convert romanized Hindi amounts. */
    val hinglish: Boolean = false,
    /** Partial (still-decoding) results get only the cheap, stable stages to avoid flicker. */
    val partial: Boolean = false,
)

internal class Ctx(val options: VoicePPOptions) {
    val indian: Boolean = options.locale.country == "IN" || options.locale.language == "hi"
    val british: Boolean = options.locale.country == "GB"
}

/**
 * Offline dictation formatter: Whisper transcript in, ready-to-insert text out.
 *
 * Pipeline: hallucination cleanup → tokenize → spoken commands → fillers → personal vocabulary →
 * technical terms → lists → numbers → paragraphs → punctuation repair → capitalization → render.
 * Stage order matters: each stage sees the tokens produced by the previous ones, and only
 * [Renderer] decides spacing.
 */
object VoicePipeline {
    fun process(raw: String, options: VoicePPOptions = VoicePPOptions()): String {
        if (raw.isBlank()) return raw.trim()
        val ctx = Ctx(options)
        val text = Hallucinations.cleanText(if (options.hinglish) Hinglish.transliterate(raw) else raw)
        var tokens = Tokenizer.tokenize(text)
        Hallucinations.collapseLoops(tokens)

        if (options.spokenPunctuation) SpokenCommands.apply(tokens)
        if (options.removeFillers) Fillers.apply(tokens, options.removeVerbalTics)
        if (options.hinglish) Hinglish.normalizeSpellings(tokens)
        if (options.vocabulary.isNotEmpty()) Vocabulary(options.vocabulary).apply(tokens)
        if (!options.partial) {
            if (options.filenameRecognition) TechTerms.apply(tokens)
            if (options.formatLists) Lists.apply(tokens)
            if (options.numberWords && options.hinglish) Hinglish.numbers(tokens, ctx)
            if (options.numberWords) NumberNormalizer.apply(tokens, ctx)
            if (options.smartParagraphs) Paragraphs.apply(tokens)
        }
        tokens = Renderer.normalizePunctuation(tokens)
        if (options.autoCapitalize) Capitalizer.apply(tokens)
        return Renderer.render(tokens)
    }
}
