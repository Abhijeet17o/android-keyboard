package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Numbered and bulleted lists. A numbered list needs an explicit, in-order sequence of markers
 * that each open a clause ("First, … Second, …", "number one … number two"), so a stray ordinal
 * ("at first I thought … a second later") never turns prose into a list.
 */
internal object Lists {
    private val ORDINAL_MARKERS = mapOf(
        "first" to 1, "firstly" to 1, "second" to 2, "secondly" to 2, "third" to 3, "thirdly" to 3,
        "fourth" to 4, "fourthly" to 4, "fifth" to 5, "fifthly" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10
    )
    private val COUNT_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    private class Marker(val index: Int, val length: Int, val value: Int, val clauseStart: Boolean)

    fun apply(tokens: MutableList<Token>) {
        bullets(tokens)
        numbered(tokens)
    }

    private fun markerAt(tokens: List<Token>, i: Int): Marker? {
        val t = tokens[i]
        if (!t.isWord || t.locked) return null
        val clauseStart = tokens.isClauseStart(i, includeComma = true)
        ORDINAL_MARKERS[t.lower]?.let { return Marker(i, 1, it, clauseStart) }
        if (clauseStart && (t.lower == "number" || t.lower == "point" || t.lower == "step")) {
            val n = tokens.getOrNull(i + 1)
            val v = if (n != null && n.isWord) COUNT_WORDS[n.lower] ?: n.text.toIntOrNull() else null
            if (v != null) return Marker(i, 2, v, true)
        }
        return null
    }

    private fun numbered(tokens: MutableList<Token>) {
        val all = tokens.indices.mapNotNull { markerAt(tokens, it) }
        // In-order run 1, 2, 3, ... of the same marker style, starting at a clause start.
        val chain = ArrayList<Marker>()
        for (m in all) {
            if (chain.isEmpty()) { if (m.value == 1 && m.clauseStart) chain.add(m); continue }
            if (m.value == chain.last().value + 1 && m.length == chain.last().length) chain.add(m)
        }
        // Two markers must both open a clause; three or more in order are a list even without commas.
        if (chain.size < 2 || (chain.size == 2 && !chain.all { it.clauseStart })) return
        // "first line … second line", "first floor … second floor": the ordinals are adjectives.
        val following = chain.map { m -> tokens.getOrNull(m.index + m.length)?.takeIf { it.isWord }?.lower }
        if (chain.all { it.length == 1 } && following[0] != null && following.all { it == following[0] }) return

        // Rewrite from the back so earlier indices stay valid.
        for ((n, m) in chain.withIndex().reversed()) {
            var at = m.index
            repeat(m.length) { tokens.removeAt(at) }
            // Drop a comma/colon spoken right after the marker: "First, wash" → "1. Wash".
            while (at < tokens.size && tokens[at].isPunct && tokens[at].text in ",:") tokens.removeAt(at)
            tokens.add(at, Token.word("${m.value}.", locked = true))

            if (at > 0) {
                // The item before ends here: drop its trailing comma, keep a real full stop.
                while (at > 0 && tokens[at - 1].isPunct && tokens[at - 1].text in ",;") {
                    tokens.removeAt(at - 1); at--
                }
                if (n == 0) {
                    // Introductory sentence: "Here are the steps" → "Here are the steps:"
                    val p = tokens[at - 1]
                    if (p.isWord) { tokens.add(at, Token.punct(":")); at++ }
                }
                if (!tokens[at - 1].isNewline) tokens.add(at, Token.newline(false))
            }
        }
    }

    private fun bullets(tokens: MutableList<Token>) {
        var i = 0
        var seen = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val isBullet = t.isWord && !t.locked && (t.lower == "bullet" || t.lower == "next") &&
                    tokens.getOrNull(i + 1)?.let { it.isWord && it.lower == "point" } == true &&
                    (t.lower == "bullet" || seen > 0)
            if (!isBullet) { i++; continue }
            tokens.removeAt(i); tokens.removeAt(i)
            while (i < tokens.size && tokens[i].isPunct && tokens[i].text in ",:.") tokens.removeAt(i)
            var at = i
            while (at > 0 && tokens[at - 1].isPunct && tokens[at - 1].text in ",;") { tokens.removeAt(at - 1); at-- }
            tokens.add(at, Token.word("•", locked = true))
            if (at > 0 && !tokens[at - 1].isNewline) tokens.add(at, Token.newline(false))
            seen++
            i = at + 2
        }
    }
}
