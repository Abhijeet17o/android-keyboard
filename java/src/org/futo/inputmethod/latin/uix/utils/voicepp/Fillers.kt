package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Removes hesitations ("um", "uh") and stutters ("I I think"). Verbal tics that sometimes carry
 * meaning ("you know", "basically", "I mean") are opt-in and only removed when set off by commas
 * or at the start of a sentence, so "do you know him" and "what I mean is" survive.
 */
internal object Fillers {
    private val HESITATIONS = setOf(
        "um", "umm", "ummm", "uh", "uhh", "uhm", "hmm", "hmmm", "hm", "mm", "mmm", "er", "erm", "uh-huh"
    )
    private val STUTTER_WORDS = setOf(
        "i", "the", "a", "an", "to", "and", "we", "it", "so", "but", "my", "you", "he", "she", "they",
        "in", "of", "is", "i'm", "it's", "this", "for", "on", "with", "can", "will", "be", "if", "or"
    )
    private val TICS: List<List<String>> = listOf(
        "you know", "i mean", "basically", "literally", "actually", "like", "you see", "kind of like"
    ).map { it.split(' ') }.sortedByDescending { it.size }

    fun apply(tokens: MutableList<Token>, removeTics: Boolean) {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (!t.isWord || t.locked) { i++; continue }

            if (t.lower in HESITATIONS) { tokens.removeAt(i); continue }

            // "I I think", "the, the plan"
            if (t.lower in STUTTER_WORDS) {
                var k = i + 1
                if (tokens.getOrNull(k)?.text == ",") k++
                val n = tokens.getOrNull(k)
                if (n != null && n.isWord && n.lower == t.lower) {
                    for (r in i until k) tokens.removeAt(i)
                    continue
                }
            }

            if (removeTics) {
                val len = ticAt(tokens, i)
                if (len > 0) { repeat(len) { tokens.removeAt(i) }; continue }
            }
            i++
        }
    }

    private fun ticAt(tokens: List<Token>, i: Int): Int {
        for (tic in TICS) {
            if (i + tic.size > tokens.size) continue
            if (tic.indices.any { k -> !tokens[i + k].isWord || tokens[i + k].lower != tic[k] }) continue
            val before = tokens.getOrNull(i - 1)
            val after = tokens.getOrNull(i + tic.size)
            val setOff = (before?.text == "," || tokens.isClauseStart(i)) &&
                    (after == null || after.isPunct)
            val commaBoth = before?.text == "," && after?.text == ","
            // "like" is only a filler when fully set off by commas: ", like, ".
            if (tic == listOf("like")) { if (commaBoth) return 1 else continue }
            if (setOff || commaBoth) return tic.size
        }
        return 0
    }
}
