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

    @JvmStatic
    fun process(raw: String): String {
        var t = raw

        // 1. Filler words
        val fillerRe = Regex("""(?i)\b(um+|uh+|hmm+|\ber\b|\bah\b|like you know|you know|i mean|sort of|kind of|basically|literally|actually)\b[\s,]*""")
        t = fillerRe.replace(t, " ").trim()

        // 2. Phone: 7-10 individually spoken digit words -> formatted number
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

        // 3. "X dollars and Y cents" -> "$X.YY"
        val centsRe = Regex("""(?i)\b([\w\s-]+?)\s+dollars?\s+and\s+([\w\s-]+?)\s+cents?\b""")
        t = centsRe.replace(t) { mr ->
            val d = wordsToInt(mr.groupValues[1])
            val c = wordsToInt(mr.groupValues[2])
            if (d != null && c != null) "\$$d.${c.toString().padStart(2, '0')}" else mr.value
        }

        // 4. "X percent" -> "X%"
        val pctRe = Regex("""(?i)\b([\w\s-]+?)\s+percent\b""")
        t = pctRe.replace(t) { mr ->
            val n = wordsToInt(mr.groupValues[1])
            if (n != null) "$n%" else mr.value
        }

        // 5. Time "two PM" -> "2 PM"
        val timeRe = Regex("""(?i)\b([\w\s-]+?)\s+(AM|PM)\b""")
        t = timeRe.replace(t) { mr ->
            val n = wordsToInt(mr.groupValues[1])
            if (n != null) "$n ${mr.groupValues[2].uppercase()}" else mr.value
        }

        // 6. Currency "forty nine dollars" -> "$49"
        val currencies = mapOf("dollar" to "$", "pound" to "£", "euro" to "€", "rupee" to "₹", "yen" to "¥")
        currencies.forEach { (word, sym) ->
            val re = Regex("""(?i)\b([\w\s-]+?)\s+${word}s?\b""")
            t = re.replace(t) { mr ->
                val n = wordsToInt(mr.groupValues[1])
                if (n != null) "$sym$n" else mr.value
            }
        }

        // 7. Ordinal dates "January fifteenth" -> "January 15th"
        val months = "january|february|march|april|may|june|july|august|september|october|november|december"
        val ordKeys = ORDINALS.keys.sortedByDescending { it.length }.joinToString("|")
        val dateRe = Regex("""(?i)\b($months)\s+($ordKeys)\b""")
        t = dateRe.replace(t) { mr ->
            val month = mr.groupValues[1].replaceFirstChar { it.uppercase() }
            val n = ORDINALS[mr.groupValues[2].lowercase()]
            if (n != null) "$month $n${ordSuffix(n)}" else mr.value
        }

        // 8. Numbered lists "first ... second ... third ..."
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

        // 9. General number words -> digits "forty nine" -> "49"
        val allNums = (ONES.keys + TENS.keys).sortedByDescending { it.length }.joinToString("|")
        val numRe = Regex("""\b((?:(?:$allNums)[- ]?)+)\b""", RegexOption.IGNORE_CASE)
        t = numRe.replace(t) { mr ->
            val n = wordsToInt(mr.value)
            if (n != null) n.toString() else mr.value
        }

        return t.replace(Regex("""\s{2,}"""), " ").trim()
    }
}
