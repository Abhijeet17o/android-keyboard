package org.futo.inputmethod.latin.uix.utils.voicepp

import java.util.Locale

internal enum class Kind { WORD, PUNCT, NEWLINE }

/**
 * One unit of dictated text. Stages rewrite the token list; only [Renderer] decides where spaces go,
 * so a stage can never accidentally glue two words together or leave a double space.
 */
internal class Token(
    var text: String,
    val kind: Kind,
    /** Render with no space before this token. */
    var glueBefore: Boolean = false,
    /** Render with no space after this token. */
    var glueAfter: Boolean = false,
    /** Final formatting (e.g. a list marker); later stages must not rewrite it. */
    var locked: Boolean = false,
) {
    val lower: String get() = text.lowercase(Locale.ROOT)
    val isWord: Boolean get() = kind == Kind.WORD
    val isPunct: Boolean get() = kind == Kind.PUNCT
    val isNewline: Boolean get() = kind == Kind.NEWLINE

    override fun toString(): String = text

    companion object {
        fun word(text: String, locked: Boolean = false) = Token(text, Kind.WORD, locked = locked)
        fun punct(text: String) = Token(text, Kind.PUNCT)
        fun newline(paragraph: Boolean) = Token(if (paragraph) "\n\n" else "\n", Kind.NEWLINE)
    }
}

internal const val SENTENCE_END = ".!?"

internal fun Token?.isSentenceEnd(): Boolean = this != null && isPunct && text.length == 1 && text[0] in SENTENCE_END

/** True at the start of the text or after a sentence end / newline / list marker. */
internal fun List<Token>.isClauseStart(i: Int, includeComma: Boolean = false): Boolean {
    if (i == 0) return true
    val p = this[i - 1]
    return p.isNewline || p.isSentenceEnd() || p.locked ||
            (p.isPunct && (p.text == ";" || p.text == ":" || (includeComma && p.text == ",")))
}
