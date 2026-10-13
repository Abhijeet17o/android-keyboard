package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Opt-in: start a new paragraph when a sentence opens with a strong topic-change phrase,
 * once at least two sentences have been dictated. Explicit "new paragraph" is handled by
 * [SpokenCommands] regardless of this setting.
 */
internal object Paragraphs {
    private val MARKERS: List<List<String>> = listOf(
        "moving on", "next up", "in conclusion", "to summarize", "to sum up", "to conclude",
        "on another note", "on a different note", "on a separate note", "changing topics", "one more thing"
    ).map { it.split(' ') }.sortedByDescending { it.size }

    fun apply(tokens: MutableList<Token>) {
        var sentences = 0
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isSentenceEnd()) sentences++
            if (t.isNewline) sentences = 0
            if (i > 0 && tokens[i - 1].isSentenceEnd() && sentences >= 2 && markerAt(tokens, i)) {
                tokens.add(i, Token.newline(true))
                sentences = 0
                i++
            }
            i++
        }
    }

    private fun markerAt(tokens: List<Token>, i: Int): Boolean = MARKERS.any { m ->
        i + m.size <= tokens.size && m.indices.all { k -> tokens[i + k].isWord && tokens[i + k].lower == m[k] }
    }
}
