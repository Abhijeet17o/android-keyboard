package org.futo.inputmethod.latin.uix.utils.voicepp

internal object Renderer {
    private val NO_SPACE_BEFORE = setOf(",", ".", "!", "?", ";", ":", "…", "...", ")", "]", "}", "%", "”", "’")
    private val NO_SPACE_AFTER = setOf("(", "[", "{", "“", "‘", "¿", "¡")
    private val CLAUSE_PUNCT = setOf(",", ";", ":")

    /**
     * Repairs punctuation left behind by Whisper and by earlier stages: duplicate or conflicting
     * marks (`,,` `, .` `. .`), punctuation at the start of a line, and runs of line breaks.
     */
    fun normalizePunctuation(tokens: List<Token>): MutableList<Token> {
        val out = ArrayList<Token>(tokens.size)
        for (t in tokens) {
            val prev = out.lastOrNull()
            when {
                t.isPunct && t.text in CLAUSE_PUNCT -> {
                    if (prev == null || prev.isNewline || prev.isSentenceEnd()) continue
                    if (prev.isPunct && prev.text in CLAUSE_PUNCT) {
                        // ", :" → ":" (the stronger mark wins); ", ," → ","
                        if (t.text != ",") out[out.size - 1] = t
                        continue
                    }
                    out.add(t)
                }
                t.isSentenceEnd() -> {
                    if (prev == null || prev.isNewline) continue
                    if (prev.isPunct && prev.text in CLAUSE_PUNCT) out.removeAt(out.size - 1)
                    val p = out.lastOrNull()
                    if (p.isSentenceEnd()) {
                        // "?." → "?", ". ?" → "?", ". ." → "."
                        if (t.text != ".") out[out.size - 1] = t
                        continue
                    }
                    out.add(t)
                }
                t.isNewline -> {
                    val p = out.lastOrNull()
                    if (p == null) continue
                    if (p.isNewline) {
                        if (t.text.length > p.text.length) out[out.size - 1] = t
                        continue
                    }
                    out.add(t)
                }
                else -> out.add(t)
            }
        }
        while (out.lastOrNull()?.isNewline == true) out.removeAt(out.size - 1)
        return out
    }

    fun render(tokens: List<Token>): String {
        val sb = StringBuilder()
        var prev: Token? = null
        for (t in tokens) {
            if (prev != null && needsSpace(prev, t)) sb.append(' ')
            sb.append(t.text)
            prev = t
        }
        return sb.toString()
    }

    private fun needsSpace(a: Token, b: Token): Boolean = !(
            a.isNewline || b.isNewline ||
            a.glueAfter || b.glueBefore ||
            (b.isPunct && b.text in NO_SPACE_BEFORE) ||
            (a.isPunct && a.text in NO_SPACE_AFTER)
    )
}
