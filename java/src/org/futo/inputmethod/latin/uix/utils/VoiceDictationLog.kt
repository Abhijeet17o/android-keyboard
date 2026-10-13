package org.futo.inputmethod.latin.uix.utils

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Opt-in local log of "raw Whisper output → inserted text" pairs, used to collect real dictation
 * failures for the voicepp golden tests. Stays on the device; the user shares it explicitly.
 */
object VoiceDictationLog {
    private const val FILE_NAME = "voice_dictation_log.txt"
    private const val MAX_BYTES = 256 * 1024

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(" | ", " \\| ")

    /** Appends one line in voicepp_golden.txt format, so entries can be pasted into the test file. */
    @Synchronized
    fun append(context: Context, locale: String, raw: String, processed: String) {
        val f = file(context)
        if (f.length() > MAX_BYTES) {
            // Keep the newer half.
            val text = f.readText()
            f.writeText(text.substring(text.length / 2).substringAfter('\n'))
        }
        f.appendText("$locale | ${escape(raw)} | ${escape(processed)} | device\n")
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun share(context: Context) {
        val f = file(context)
        val text = if (f.exists()) f.readText() else ""
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Voice dictation log")
            putExtra(Intent.EXTRA_TEXT, text.ifEmpty { "(empty — enable the dictation log and dictate something first)" })
        }
        context.startActivity(Intent.createChooser(intent, "Share dictation log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
