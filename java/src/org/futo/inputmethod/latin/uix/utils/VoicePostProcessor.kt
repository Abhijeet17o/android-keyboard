package org.futo.inputmethod.latin.uix.utils

object VoicePostProcessor {

    private val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )
    private val ORDINALS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9,
        "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13,
        "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16,
        "seventeenth" to 17, "eighteenth" to 18, "nineteenth" to 19,
        "twentieth" to 20, "thirtieth" to 30, "fortieth" to 40,
        "fiftieth" to 50, "sixtieth" to 60, "seventieth" to 70,
        "eightieth" to 80, "ninetieth" to 90
    )
    private val PHONE_WORDS = listOf("zero","one","two","three","four","five","six","seven","eight","nine")

    // Spoken punctuation words → actual characters
    // Ordered so longer phrases match before substrings (e.g. "open bracket" before "bracket")
    private val SPOKEN_PUNCTUATION = listOf(
        // New-line variants (must be first — they produce \n, not inline chars)
        "new line"        to "\n",
        "newline"         to "\n",
        "new paragraph"   to "\n\n",
        // Sentence enders
        "period"          to ". ",
        "full stop"       to ". ",
        "exclamation mark" to "! ",
        "exclamation point" to "! ",
        "question mark"   to "? ",
        // Commas / pauses
        "comma"           to ", ",
        "semicolon"       to "; ",
        "colon"           to ": ",
        // Quotes
        "open quote"      to "\"",
        "close quote"     to "\"",
        "open single quote" to "'",
        "close single quote" to "'",
        // Brackets
        "open parenthesis" to "(",
        "close parenthesis" to ")",
        "open paren"      to "(",
        "close paren"     to ")",
        "open bracket"    to "[",
        "close bracket"   to "]",
        "open brace"      to "{",
        "close brace"     to "}",
        // Misc
        "hyphen"          to "-",
        "dash"            to "—",
        "ellipsis"        to "…",
        "at sign"         to "@",
        "hashtag"         to "#",
        "ampersand"       to "&",
        "asterisk"        to "*",
        "slash"           to "/",
        "backslash"       to "\\"
    )

    private fun wordsToInt(phrase: String): Int? {
        val tokens = phrase.lowercase().trim().split(Regex("""[\s-]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        var result = 0; var current = 0
        for (tok in tokens) {
            when {
                tok == "hundred"  -> { if (current == 0) current = 1; current *= 100 }
                tok == "thousand" -> { result += current * 1_000; current = 0 }
                tok == "million"  -> { result += current * 1_000_000; current = 0 }
                ONES.containsKey(tok) -> current += ONES[tok]!!
                TENS.containsKey(tok) -> current += TENS[tok]!!
                else -> return null
            }
        }
        return result + current
    }

    private fun ordSuffix(n: Int) = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    // ── Spoken punctuation ────────────────────────────────────────────────────

    private fun applySpokenPunctuation(text: String): String {
        var t = text
        for ((word, char) in SPOKEN_PUNCTUATION) {
            // Match the spoken word optionally preceded by a space and followed by a space/comma/end
            val re = Regex("""(?i)\s*\b${Regex.escape(word)}\b\s*""")
            t = re.replace(t, char)
        }
        return t
    }

    // ── Auto-capitalize ───────────────────────────────────────────────────────

    private fun applyAutoCapitalize(text: String): String {
        var t = text

        // 1. Standalone "i" → "I"  (not "it", "in", "is" etc.)
        t = Regex("""\bi\b""").replace(t, "I")

        // 2. Capitalize the very first letter of the entire string
        t = t.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

        // 3. Capitalize first letter after sentence-ending punctuation (. ! ?)
        //    Handles:  ". word"  / "! word"  / "? word"  / ".\nword"
        t = Regex("""([.!?][\s]+)([a-z])""").replace(t) { mr ->
            mr.groupValues[1] + mr.groupValues[2].uppercase()
        }

        return t
    }

    // ── Filler-word removal ───────────────────────────────────────────────────

    private val FILLER_RE = Regex(
        """(?i)\b(um+|uh+|hmm+|\ber\b|\bah\b|like you know|you know|i mean|sort of|kind of|basically|literally|actually)\b[\s,]*"""
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Full pipeline. Each stage can be toggled independently.
     *
     * @param raw               Raw Whisper transcript
     * @param removeFillers     Strip um/uh/hmm/filler phrases
     * @param spokenPunctuation Convert "period", "comma", "new line" etc. → actual chars
     * @param autoCapitalize    Capitalize "I", sentence starts
     * @param numberWords       Convert number words → digits, currency, phone, dates, etc.
     */
    @JvmStatic
    fun process(
        raw: String,
        removeFillers: Boolean = true,
        spokenPunctuation: Boolean = true,
        autoCapitalize: Boolean = true,
        numberWords: Boolean = true
    ): String {
        var t = raw

        // Stage 1 — Spoken punctuation (run BEFORE filler removal so "um period" → "um. " → ". ")
        if (spokenPunctuation) {
            t = applySpokenPunctuation(t)
        }

        // Stage 2 — Filler words
        if (removeFillers) {
            t = FILLER_RE.replace(t, " ").trim()
        }

        if (numberWords) {
            // Stage 3 — Phone: 7-10 individually spoken digit words → formatted number
            val phoneRe = Regex("""(?i)\b((?:(?:zero|one|two|three|four|five|six|seven|eight|nine)\s+){6,9}(?:zero|one|two|three|four|five|six|seven|eight|nine))\b""")
            t = phoneRe.replace(t) { mr ->
                val digits = mr.value.trim().split(Regex("""\s+"""))
                    .mapNotNull { w -> PHONE_WORDS.indexOf(w.lowercase()).takeIf { it >= 0 }?.toString() }
                when (digits.size) {
                    7  -> "${digits.take(3).joinToString("")}-${digits.drop(3).joinToString("")}"
                    10 -> "(${digits.take(3).joinToString("")}) ${digits.drop(3).take(3).joinToString("")}-${digits.drop(6).joinToString("")}"
                    else -> digits.joinToString("-")
                }
            }

            // Stage 4 — "X dollars and Y cents" → "$X.YY"
            val centsRe = Regex("""(?i)\b([\w\s-]+?)\s+dollars?\s+and\s+([\w\s-]+?)\s+cents?\b""")
            t = centsRe.replace(t) { mr ->
                val d = wordsToInt(mr.groupValues[1])
                val c = wordsToInt(mr.groupValues[2])
                if (d != null && c != null) "\$$d.${c.toString().padStart(2, '0')}" else mr.value
            }

            // Stage 5 — "X percent" → "X%"
            val pctRe = Regex("""(?i)\b([\w\s-]+?)\s+percent\b""")
            t = pctRe.replace(t) { mr ->
                val n = wordsToInt(mr.groupValues[1])
                if (n != null) "$n%" else mr.value
            }

            // Stage 6 — Time "two PM" → "2 PM"
            val timeRe = Regex("""(?i)\b([\w\s-]+?)\s+(AM|PM)\b""")
            t = timeRe.replace(t) { mr ->
                val n = wordsToInt(mr.groupValues[1])
                if (n != null) "$n ${mr.groupValues[2].uppercase()}" else mr.value
            }

            // Stage 7 — Currency symbols
            val currencies = mapOf("dollar" to "$", "pound" to "£", "euro" to "€", "rupee" to "₹", "yen" to "¥")
            currencies.forEach { (word, sym) ->
                val re = Regex("""(?i)\b([\w\s-]+?)\s+${word}s?\b""")
                t = re.replace(t) { mr ->
                    val n = wordsToInt(mr.groupValues[1])
                    if (n != null) "$sym$n" else mr.value
                }
            }

            // Stage 8 — Ordinal dates "January fifteenth" → "January 15th"
            val months = "january|february|march|april|may|june|july|august|september|october|november|december"
            val ordKeys = ORDINALS.keys.sortedByDescending { it.length }.joinToString("|")
            val dateRe = Regex("""(?i)\b($months)\s+($ordKeys)\b""")
            t = dateRe.replace(t) { mr ->
                val month = mr.groupValues[1].replaceFirstChar { it.uppercase() }
                val n = ORDINALS[mr.groupValues[2].lowercase()]
                if (n != null) "$month $n${ordSuffix(n)}" else mr.value
            }

            // Stage 9 — Numbered lists "first ... second ... third ..."
            val listOrd = mapOf("first" to 1,"second" to 2,"third" to 3,"fourth" to 4,
                "fifth" to 5,"sixth" to 6,"seventh" to 7,"eighth" to 8,"ninth" to 9)
            val listRe = Regex("""(?i)\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)\b""")
            if (listRe.findAll(t).count() >= 2) {
                var i = 0
                t = listRe.replace(t) { mr ->
                    val n = listOrd[mr.value.lowercase()]
                    if (n != null) "${if (i++ > 0) "\n" else ""}$n." else mr.value
                }
            }

            // Stage 10 — General number words → digits "forty nine" → "49"
            val allNums = (ONES.keys + TENS.keys).sortedByDescending { it.length }.joinToString("|")
            val numRe = Regex("""\b((?:(?:$allNums)[- ]?)+)\b""", RegexOption.IGNORE_CASE)
            t = numRe.replace(t) { mr ->
                val n = wordsToInt(mr.value)
                if (n != null) n.toString() else mr.value
            }
        }

        // Stage 11 — Auto-capitalize (run last so punctuation is already in place)
        if (autoCapitalize) {
            t = applyAutoCapitalize(t)
        }

        // Collapse multiple spaces (but preserve newlines)
        t = t.split("\n").joinToString("\n") { line ->
            line.replace(Regex(""" {2,}"""), " ").trim()
        }.trim()

        return t
    }
}
