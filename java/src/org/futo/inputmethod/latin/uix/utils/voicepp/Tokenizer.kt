package org.futo.inputmethod.latin.uix.utils.voicepp

import java.util.Locale

internal object Tokenizer {
    private const val LEADING = "([{\"“‘¿¡"
    private const val TRAILING = ",.!?;:)]}\"”…"

    // "e.g.", "i.e.", "a.m.", "U.S." keep their dots so they are not mistaken for sentence ends.
    private val DOTTED_ABBREVIATION = Regex("""^(?:[A-Za-z]\.){2,}$""")
    private val ABBREVIATIONS = setOf(
        "mr.", "mrs.", "ms.", "dr.", "st.", "vs.", "etc.", "jr.", "sr.", "prof.", "rs.", "no."
    )

    fun tokenize(input: String): MutableList<Token> {
        val text = input.replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<Token>()
        var quoteOpen = false

        fun addPunct(p: String) {
            val t = Token.punct(p)
            if (p == "\"") {
                if (quoteOpen) t.glueBefore = true else t.glueAfter = true
                quoteOpen = !quoteOpen
            }
            out.add(t)
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                var count = 0
                while (i < text.length && text[i].isWhitespace()) {
                    if (text[i] == '\n') count++
                    i++
                }
                out.add(Token.newline(paragraph = count >= 2))
                continue
            }
            if (c.isWhitespace()) { i++; continue }

            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            splitChunk(text.substring(i, j), out, ::addPunct)
            i = j
        }
        return out
    }

    private fun splitChunk(chunk: String, out: MutableList<Token>, addPunct: (String) -> Unit) {
        var s = chunk
        val leading = ArrayList<String>()
        while (s.isNotEmpty() && s[0] in LEADING) {
            leading.add(s[0].toString())
            s = s.substring(1)
        }

        val trailing = ArrayList<String>()
        while (s.isNotEmpty()) {
            if (DOTTED_ABBREVIATION.matches(s) || s.lowercase(Locale.ROOT) in ABBREVIATIONS) break
            if (s.endsWith("...")) {
                trailing.add(0, "...")
                s = s.dropLast(3)
                continue
            }
            if (s.last() in TRAILING) {
                trailing.add(0, s.last().toString())
                s = s.dropLast(1)
                continue
            }
            break
        }

        leading.forEach(addPunct)
        if (s.isNotEmpty()) out.add(Token.word(s))
        trailing.forEach(addPunct)
    }
}
