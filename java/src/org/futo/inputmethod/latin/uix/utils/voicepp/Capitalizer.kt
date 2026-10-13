package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Capitalizes sentence starts (after . ! ?, line breaks and list markers) and the pronoun "I".
 * Only ever upper-cases; never touches URLs, paths, emails or code-like tokens.
 */
internal object Capitalizer {
    private val I_FORMS = mapOf("i" to "I", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd")

    private fun isCodeLike(s: String) = s.any { it == '@' || it == '/' || it == '_' || it == '\\' } ||
            (s.indexOf('.') in 1 until s.length - 1)

    fun apply(tokens: List<Token>) {
        var capNext = true
        for (t in tokens) {
            when {
                t.isNewline -> capNext = true
                t.isSentenceEnd() -> capNext = true
                t.isPunct -> {}
                t.locked -> capNext = true // list marker
                t.text.isEmpty() -> {}
                t.keepCase -> capNext = false
                else -> {
                    I_FORMS[t.lower]?.let { t.text = it }
                    if (capNext && t.text[0].isLowerCase() && !isCodeLike(t.text)) {
                        t.text = t.text.replaceFirstChar { it.uppercaseChar() }
                    }
                    capNext = false
                }
            }
        }
    }
}
