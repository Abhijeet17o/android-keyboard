package org.futo.inputmethod.latin.uix.utils.voicepp

import java.util.Locale

/**
 * Applies the user's personal dictionary to the transcript:
 *  - exact matches take the dictionary's spelling and casing ("github" → "GitHub", "futo" → "FUTO");
 *  - words Whisper split are joined ("Abhi jeet" → "Abhijeet", "Git Hub" → "GitHub");
 *  - a name Whisper capitalised but misspelled is replaced when it sounds the same and is a near
 *    spelling ("Abhijit" → "Abhijeet"). Lowercase words are never fuzzily replaced, so ordinary
 *    vocabulary cannot be rewritten into a name.
 */
internal class Vocabulary(entries: List<String>) {
    private val exact = HashMap<String, String>()
    private val joined = HashMap<String, String>()
    private val byKey = HashMap<String, MutableList<String>>()

    init {
        for (e in entries) {
            val word = e.trim()
            if (word.isEmpty() || ' ' in word || word.length > 40) continue
            val lower = word.lowercase(Locale.ROOT)
            // "US", "IT": recasing would hijack the ordinary words "us", "it".
            val shortAcronym = word.length <= 3 && word.all { !it.isLetter() || it.isUpperCase() }
            if (!shortAcronym) exact.putIfAbsent(lower, word)
            // Only join into words with capitals (names, brands), never plain words ("a round" ≠ "around").
            if (word.any { it.isUpperCase() }) joined.putIfAbsent(lower.filter { it.isLetterOrDigit() }, word)
            if (word.length >= 4 && word.all { it.isLetter() }) {
                byKey.getOrPut(phoneticKey(lower)) { ArrayList() }.add(word)
            }
        }
    }

    val isEmpty get() = exact.isEmpty()

    fun apply(tokens: MutableList<Token>) {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (!t.isWord || t.locked) { i++; continue }

            // Two or three tokens that together spell a dictionary word.
            var joinedHit = false
            for (span in 3 downTo 2) {
                if (i + span > tokens.size) continue
                val parts = tokens.subList(i, i + span)
                if (parts.any { !it.isWord || it.locked }) continue
                val word = joined[parts.joinToString("") { it.lower }] ?: continue
                repeat(span - 1) { tokens.removeAt(i + 1) }
                t.text = word; t.keepCase = true
                joinedHit = true
                break
            }
            if (joinedHit) { i++; continue }

            val word = exact[t.lower]
            if (word != null) {
                if (word != t.text && !(word.all { it.isLowerCase() } && t.text.equals(word, ignoreCase = true))) {
                    t.text = word
                }
                // A plain lowercase dictionary word may still start a sentence.
                t.keepCase = word.any { it.isUpperCase() }
                i++; continue
            }

            fuzzy(tokens, i)?.let { t.text = it; t.keepCase = true }
            i++
        }
    }

    private fun fuzzy(tokens: List<Token>, i: Int): String? {
        val t = tokens[i]
        val s = t.text
        if (s.length < 4 || !s.all { it.isLetter() } || !s[0].isUpperCase() || s.drop(1).any { it.isUpperCase() }) return null
        if (tokens.isClauseStart(i)) return null // capitalised only because it starts a sentence
        val candidates = byKey[phoneticKey(t.lower)] ?: return null
        val best = candidates.minByOrNull { editDistance(it.lowercase(Locale.ROOT), t.lower) } ?: return null
        val d = editDistance(best.lowercase(Locale.ROOT), t.lower)
        return if (d in 1..maxOf(1, best.length / 4)) best else null
    }

    companion object {
        /** A compact Metaphone-style key: spellings that sound alike map to the same key. */
        fun phoneticKey(word: String): String {
            var s = word.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
            if (s.isEmpty()) return ""
            s = s.replace(Regex("^(kn|gn|pn|wr|ps)"), { it.value.substring(1) })
                .replace("x", "ks")
                .replace("ph", "f").replace("ck", "k").replace("sch", "sk").replace("sh", "x")
                .replace("ch", "x").replace("th", "0").replace("dg", "j").replace("gh", "")
                .replace(Regex("c(?=[eiy])"), "s").replace('c', 'k').replace('q', 'k')
                .replace('z', 's').replace('v', 'f').replace('w', 'v')
            val first = s[0]
            val sb = StringBuilder()
            sb.append(if (first in "aeiouy") 'a' else first)
            for (k in 1 until s.length) {
                val c = s[k]
                if (c in "aeiouyh") continue
                if (sb.last() != c) sb.append(c)
            }
            return sb.toString()
        }

        fun editDistance(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]
                dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }
    }
}
