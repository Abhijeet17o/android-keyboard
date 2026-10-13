package org.futo.inputmethod.latin.uix.utils

import org.futo.inputmethod.latin.uix.utils.voicepp.VoicePPOptions
import org.futo.inputmethod.latin.uix.utils.voicepp.VoicePipeline
import java.util.Locale

/** Entry point for formatting Whisper output; see [VoicePipeline] for the stages. */
object VoicePostProcessor {

    /**
     * @param raw                 Raw Whisper transcript
     * @param removeFillers       Strip hesitations (um/uh) and stutters
     * @param removeVerbalTics    Also strip "you know", "basically", "I mean" when set off by commas
     * @param spokenPunctuation   "comma"/"question mark"/"new line"/"scratch that" → punctuation and edits
     * @param autoCapitalize      Capitalize "I" and sentence starts
     * @param numberWords         Numbers, currency, times, dates, units, phone numbers
     * @param filenameRecognition "dot py" → .py, "underscore" → _, URLs and emails
     * @param smartParagraphs     Paragraph break on topic-change phrases ("moving on", "in conclusion")
     * @param formatLists         "first … second … third" → numbered list, "bullet point" → •
     * @param locale              Active keyboard locale; en-IN selects ₹ and lakh/crore grouping
     * @param partial             Still-decoding result: run only the cheap, stable stages
     */
    @JvmStatic
    fun process(
        raw: String,
        removeFillers: Boolean = true,
        spokenPunctuation: Boolean = true,
        autoCapitalize: Boolean = true,
        numberWords: Boolean = true,
        filenameRecognition: Boolean = true,
        smartParagraphs: Boolean = false,
        removeVerbalTics: Boolean = false,
        formatLists: Boolean = true,
        locale: Locale = Locale.getDefault(),
        partial: Boolean = false,
    ): String {
        val options = VoicePPOptions(
            removeFillers = removeFillers,
            removeVerbalTics = removeVerbalTics,
            spokenPunctuation = spokenPunctuation,
            autoCapitalize = autoCapitalize,
            numberWords = numberWords,
            filenameRecognition = filenameRecognition,
            smartParagraphs = smartParagraphs,
            formatLists = formatLists,
            locale = locale,
            partial = partial,
        )
        // A formatting bug must never lose what the user said.
        return try {
            VoicePipeline.process(raw, options)
        } catch (e: Exception) {
            raw
        }
    }
}
