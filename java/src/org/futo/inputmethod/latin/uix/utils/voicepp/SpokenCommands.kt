package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Dictation commands: "comma", "question mark", "new line", "open quote … close quote",
 * "scratch that". Whisper usually adds its own punctuation around a spoken command
 * ("Hello, comma, world."); that guessed punctuation is absorbed so it is not doubled.
 */
internal object SpokenCommands {
    private sealed class Action {
        /** [ambiguous] words ("period", "colon") are only commands in command position. */
        class Mark(val text: String, val ambiguous: Boolean = false) : Action()
        class Break(val paragraph: Boolean) : Action()
        class Open(val text: String) : Action()
        class Close(val text: String) : Action()
        class Symbol(val text: String, val glueBefore: Boolean, val glueAfter: Boolean) : Action()
        object Scratch : Action()
    }

    private val COMMANDS: List<Pair<List<String>, Action>> = listOf(
        "new paragraph" to Action.Break(true),
        "next paragraph" to Action.Break(true),
        "new line" to Action.Break(false),
        "newline" to Action.Break(false),
        "next line" to Action.Break(false),
        "question mark" to Action.Mark("?"),
        "exclamation mark" to Action.Mark("!"),
        "exclamation point" to Action.Mark("!"),
        "full stop" to Action.Mark("."),
        "period" to Action.Mark(".", ambiguous = true),
        "comma" to Action.Mark(","),
        "semicolon" to Action.Mark(";"),
        "semi colon" to Action.Mark(";"),
        "colon" to Action.Mark(":", ambiguous = true),
        "ellipsis" to Action.Mark("…"),
        "dot dot dot" to Action.Mark("…"),
        "open parenthesis" to Action.Open("("),
        "open parentheses" to Action.Open("("),
        "open paren" to Action.Open("("),
        "open bracket" to Action.Open("("),
        "open round bracket" to Action.Open("("),
        "open square bracket" to Action.Open("["),
        "open curly bracket" to Action.Open("{"),
        "open brace" to Action.Open("{"),
        "close parenthesis" to Action.Close(")"),
        "close parentheses" to Action.Close(")"),
        "close paren" to Action.Close(")"),
        "close bracket" to Action.Close(")"),
        "close round bracket" to Action.Close(")"),
        "close square bracket" to Action.Close("]"),
        "close curly bracket" to Action.Close("}"),
        "close brace" to Action.Close("}"),
        "open quote" to Action.Open("\""),
        "begin quote" to Action.Open("\""),
        "start quote" to Action.Open("\""),
        "close quote" to Action.Close("\""),
        "end quote" to Action.Close("\""),
        "unquote" to Action.Close("\""),
        "open single quote" to Action.Open("'"),
        "close single quote" to Action.Close("'"),
        "ampersand" to Action.Symbol("&", glueBefore = false, glueAfter = false),
        "asterisk" to Action.Symbol("*", glueBefore = false, glueAfter = false),
        "hashtag" to Action.Symbol("#", glueBefore = false, glueAfter = true),
        "hash tag" to Action.Symbol("#", glueBefore = false, glueAfter = true),
        "percent sign" to Action.Symbol("%", glueBefore = true, glueAfter = false),
        "dollar sign" to Action.Symbol("$", glueBefore = false, glueAfter = true),
        "rupee sign" to Action.Symbol("₹", glueBefore = false, glueAfter = true),
        "em dash" to Action.Symbol("—", glueBefore = false, glueAfter = false),
        "scratch that" to Action.Scratch,
        "delete that" to Action.Scratch,
        "strike that" to Action.Scratch,
    ).map { (phrase, action) -> phrase.split(' ') to action }
        .sortedByDescending { it.first.size }

    // "a new line of products", "the comma is misplaced": the command word used as a noun.
    private val DETERMINERS = setOf(
        "a", "an", "the", "this", "that", "every", "each", "no", "any", "another", "which", "one", "my", "your"
    )
    // Nouns commonly followed by "period" / "colon" in normal speech.
    private val PERIOD_COLLOCATIONS = setOf(
        "trial", "grace", "time", "waiting", "notice", "cooling", "probation", "probationary", "billing",
        "rest", "study", "transition", "warranty", "payment", "test", "sale", "holiday", "lock-in",
        "incubation", "reporting", "accounting", "fiscal", "school", "class", "free", "same", "short",
        "long", "initial", "two-week", "lunch", "prep", "exam", "rental", "contract", "menstrual",
        "transverse", "sigmoid", "ascending", "descending", "spastic", "healthy", "irritable"
    )
    private val ABSORBABLE = setOf(",", ".", ";", ":", "!", "?")
    private val I_FORMS = setOf("i", "i'm", "i'll", "i've", "i'd")

    fun apply(tokens: MutableList<Token>) {
        var i = 0
        while (i < tokens.size) {
            val hit = matchAt(tokens, i)
            if (hit == null) { i++; continue }
            val (len, action) = hit
            i = execute(tokens, i, len, action)
        }
    }

    private fun matchAt(tokens: List<Token>, i: Int): Pair<Int, Action>? {
        if (!tokens[i].isWord || tokens[i].locked) return null
        for ((words, action) in COMMANDS) {
            if (i + words.size > tokens.size) continue
            var ok = true
            for (k in words.indices) {
                val t = tokens[i + k]
                if (!t.isWord || t.lower != words[k]) { ok = false; break }
            }
            if (!ok) continue
            if (!isCommandPosition(tokens, i, words.size, action)) continue
            return words.size to action
        }
        return null
    }

    private fun isCommandPosition(tokens: List<Token>, i: Int, len: Int, action: Action): Boolean {
        val prev = tokens.getOrNull(i - 1)
        val next = tokens.getOrNull(i + len)
        if (prev != null && prev.isWord && prev.lower in DETERMINERS) return false
        if (action is Action.Break && next != null && next.isWord) {
            if (next.lower == "of") return false
            // "New line characters are tricky." — Whisper capitalised it as the start of a sentence
            // that keeps going, so it is prose, not a command.
            if (tokens[i].text[0].isUpperCase() && tokens.isClauseStart(i) && next.text[0].isLowerCase()) return false
        }
        if (action is Action.Mark && action.ambiguous) {
            if (prev != null && prev.isWord && prev.lower in PERIOD_COLLOCATIONS) return false
            val atBoundary = next == null || next.isPunct || next.isNewline ||
                    (next.isWord && next.text[0].isUpperCase() && next.lower !in I_FORMS) ||
                    (prev != null && prev.isPunct)
            if (!atBoundary) return false
        }
        return true
    }

    /** Applies the command at [i] (spanning [len] tokens); returns the index to continue from. */
    private fun execute(tokens: MutableList<Token>, i: Int, len: Int, action: Action): Int {
        if (action is Action.Scratch) return scratch(tokens, i, len)

        repeat(len) { tokens.removeAt(i) }
        var at = i

        // Whisper's guessed punctuation right after the command.
        var removedSentenceEnd = false
        while (at < tokens.size && tokens[at].isPunct && tokens[at].text in ABSORBABLE &&
            !(action is Action.Close)) {
            if (tokens[at].isSentenceEnd()) removedSentenceEnd = true
            tokens.removeAt(at)
        }
        // ... and right before it (but keep a real full stop before a line break).
        if (action is Action.Mark || action is Action.Close) {
            while (at > 0 && tokens[at - 1].isPunct && tokens[at - 1].text in ABSORBABLE) {
                tokens.removeAt(at - 1)
                at--
            }
        }

        val token = when (action) {
            is Action.Mark -> Token.punct(action.text)
            is Action.Break -> Token.newline(action.paragraph)
            is Action.Open -> Token.punct(action.text).also { it.glueAfter = true }
            is Action.Close -> Token.punct(action.text).also { it.glueBefore = true }
            is Action.Symbol -> Token.word(action.text).also {
                it.glueBefore = action.glueBefore; it.glueAfter = action.glueAfter
            }
            Action.Scratch -> error("handled above")
        }
        tokens.add(at, token)

        // "Hello comma. How are you" — Whisper started a new sentence that the comma continues.
        val next = tokens.getOrNull(at + 1)
        if (action is Action.Mark && action.text in ",;:" && removedSentenceEnd && next != null &&
            next.isWord && next.lower !in I_FORMS && next.text.length > 1 &&
            next.text[0].isUpperCase() && next.text.drop(1).none { it.isUpperCase() }) {
            next.text = next.text.replaceFirstChar { it.lowercaseChar() }
        }
        return at + 1
    }

    /** "scratch that": drop the sentence (or clause after a line break) dictated before it. */
    private fun scratch(tokens: MutableList<Token>, i: Int, len: Int): Int {
        var end = i + len
        while (end < tokens.size && tokens[end].isPunct && tokens[end].text in ABSORBABLE) end++
        var start = clauseStartBefore(tokens, i)
        // "Buy milk. Actually, scratch that." — the clause holds no content, so the user means the
        // sentence before it.
        if ((start until i).all { tokens[it].isPunct || tokens[it].lower in CORRECTION_WORDS }) {
            start = clauseStartBefore(tokens, start)
        }
        for (k in start until end) tokens.removeAt(start)
        return start
    }

    private val CORRECTION_WORDS = setOf("actually", "no", "wait", "sorry", "oops", "oh", "um", "uh", "okay", "ok")

    private fun clauseStartBefore(tokens: List<Token>, i: Int): Int {
        var start = i
        // Skip Whisper's punctuation between the clause and the command.
        while (start > 0 && tokens[start - 1].isPunct && tokens[start - 1].text in ABSORBABLE) start--
        while (start > 0 && !(tokens[start - 1].isSentenceEnd() || tokens[start - 1].isNewline)) start--
        return start
    }
}
