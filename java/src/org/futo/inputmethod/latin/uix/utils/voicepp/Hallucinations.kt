package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Removes Whisper failure artifacts: decoding loops ("I'm going to go. I'm going to go. I'm going
 * to go.") and caption boilerplate it learned from subtitles ("Subtitles by the Amara.org community").
 */
internal object Hallucinations {
    // A sentence ends at . ! ? followed by whitespace or the end, so "Amara.org" is not a boundary.
    private const val TO_SENTENCE_END = """.*?(?:[.!?](?=\s|$)|$)"""
    private val BOILERPLATE = listOf(
        Regex("""(?i)\bsubtitles? (?:by|created by)\b$TO_SENTENCE_END"""),
        Regex("""(?i)[^.!?\n]*\bamara\.org\b$TO_SENTENCE_END"""),
        Regex("""(?i)\[(?:blank_audio|music|silence|inaudible)]|\((?:music|upbeat music|silence)\)"""),
    )

    fun cleanText(raw: String): String {
        // The patterns backtrack; skip them entirely for the normal case.
        if (!raw.contains("subtitle", ignoreCase = true) && !raw.contains("amara", ignoreCase = true) &&
            '[' !in raw && '(' !in raw) return raw
        var t = raw
        for (re in BOILERPLATE) t = re.replace(t, " ")
        return t
    }

    /** Collapses a phrase repeated back-to-back: 3+ times for phrases, 5+ times for single words. */
    fun collapseLoops(tokens: MutableList<Token>) {
        // Shortest units first, so "Thanks ×6" collapses as one word, not as "Thanks Thanks" ×3.
        for (n in 1..12) {
            val minRepeats = if (n == 1) 5 else 3
            var i = 0
            while (i + n * minRepeats <= tokens.size) {
                var repeats = 1
                while (i + n * (repeats + 1) <= tokens.size && sameSpan(tokens, i, i + n * repeats, n)) repeats++
                if (repeats >= minRepeats && tokens.subList(i, i + n).any { it.isWord }) {
                    repeat(n * (repeats - 1)) { tokens.removeAt(i + n) }
                }
                i++
            }
        }
    }

    private fun sameSpan(tokens: List<Token>, a: Int, b: Int, n: Int): Boolean =
        (0 until n).all { k -> tokens[a + k].lower == tokens[b + k].lower && tokens[a + k].kind == tokens[b + k].kind }
}
