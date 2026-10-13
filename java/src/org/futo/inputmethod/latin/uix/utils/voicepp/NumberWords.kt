package org.futo.inputmethod.latin.uix.utils.voicepp

/** Spoken-number vocabulary and a strict grammar-based parser over lowercase words. */
internal object NumberWords {
    val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9
    )
    val TEENS = mapOf(
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )
    val SCALES = mapOf(
        "thousand" to 1_000L,
        "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "million" to 1_000_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L,
        "billion" to 1_000_000_000L,
    )
    /** Scales that read better as words after the digits: "2 lakh", "1.5 crore", "3 million". */
    val WORD_SCALES = setOf("lakh", "lakhs", "lac", "lacs", "crore", "crores", "million", "billion")

    val ORD_ONES = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9
    )
    val ORD_TEENS = mapOf(
        "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
        "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18, "nineteenth" to 19
    )
    val ORD_TENS = mapOf(
        "twentieth" to 20, "thirtieth" to 30, "fortieth" to 40, "fiftieth" to 50,
        "sixtieth" to 60, "seventieth" to 70, "eightieth" to 80, "ninetieth" to 90
    )

    fun isCardinalWord(w: String) = w in ONES || w in TEENS || w in TENS || w == "hundred" || w in SCALES
    fun isNumberWord(w: String) = isCardinalWord(w) || w in ORD_ONES || w in ORD_TEENS || w in ORD_TENS ||
            w == "hundredth" || w == "thousandth"

    class Cardinal(
        val end: Int,
        val value: Long,
        val ordinal: Boolean,
        val hasHundred: Boolean,
        val scaleCount: Int,
        val lastWord: String,
    )

    private enum class Last { NONE, ZERO, ONES, TENS, ONES_AFTER_TENS, TEEN, HUNDRED, SCALE }

    /**
     * Parses the longest grammatical cardinal (or ordinal) starting at [start]:
     * "three hundred and fifty", "two lakh fifty thousand", "a hundred", "twenty first".
     * Stops at the first word that cannot continue the number, so "five six" parses as just "five".
     */
    fun parseCardinal(w: List<String>, start: Int): Cardinal? {
        var total = 0L
        var group = 0L
        var last = Last.NONE
        var lastScale = Long.MAX_VALUE
        var groupHasHundred = false
        var hasHundred = false
        var scaleCount = 0
        var ordinal = false
        var j = start

        fun canStartGroup() = last == Last.NONE || last == Last.HUNDRED || last == Last.SCALE

        loop@ while (j < w.size && !ordinal) {
            val x = w[j]
            val next = w.getOrNull(j + 1)
            when {
                x == "a" && last == Last.NONE && (next == "hundred" || next in SCALES) -> {
                    group = 1; last = Last.ONES
                }
                x == "and" -> {
                    val ok = (last == Last.HUNDRED || last == Last.SCALE) && next != null &&
                            ((next in ONES && next != "zero") || next in TEENS || next in TENS ||
                                    next in ORD_ONES || next in ORD_TEENS || next in ORD_TENS)
                    if (!ok) break@loop
                }
                x == "zero" -> {
                    if (last != Last.NONE) break@loop
                    last = Last.ZERO; j++; break@loop
                }
                x in ONES || x in ORD_ONES -> {
                    val v = ONES[x] ?: ORD_ONES[x]!!
                    last = when {
                        last == Last.TENS -> Last.ONES_AFTER_TENS
                        canStartGroup() -> Last.ONES
                        else -> break@loop
                    }
                    group += v
                    ordinal = x in ORD_ONES
                }
                x in TEENS || x in ORD_TEENS -> {
                    if (!canStartGroup()) break@loop
                    group += TEENS[x] ?: ORD_TEENS[x]!!
                    last = Last.TEEN
                    ordinal = x in ORD_TEENS
                }
                x in TENS || x in ORD_TENS -> {
                    if (!canStartGroup()) break@loop
                    group += TENS[x] ?: ORD_TENS[x]!!
                    last = Last.TENS
                    ordinal = x in ORD_TENS
                }
                x == "hundred" || x == "hundredth" -> {
                    // "two hundred", "nineteen hundred", "twenty five hundred"; never "twenty hundred".
                    val ok = (last == Last.ONES || last == Last.TEEN || last == Last.ONES_AFTER_TENS) &&
                            !groupHasHundred && group in 1..99
                    if (!ok) break@loop
                    group *= 100
                    groupHasHundred = true; hasHundred = true
                    last = Last.HUNDRED
                    ordinal = x == "hundredth"
                }
                x in SCALES || x == "thousandth" -> {
                    val s = SCALES[x] ?: 1_000L
                    if (group <= 0 || s >= lastScale || last == Last.NONE) break@loop
                    total += group * s
                    group = 0; groupHasHundred = false
                    lastScale = s; scaleCount++
                    last = Last.SCALE
                    ordinal = x == "thousandth"
                }
                else -> break@loop
            }
            j++
        }
        if (j == start || last == Last.NONE) return null
        // Never end on a dangling "a"/"and".
        while (j > start && (w[j - 1] == "and" || w[j - 1] == "a")) j--
        if (j == start) return null
        return Cardinal(j, total + group, ordinal, hasHundred, scaleCount, w[j - 1])
    }

    /** "five two nine", "nine eight double seven", "one oh one" → digit string (3+ digits). */
    fun parseDigitSequence(w: List<String>, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var j = start
        while (j < w.size) {
            val x = w[j]
            val next = w.getOrNull(j + 1)
            when {
                x in ONES -> { sb.append(ONES[x]); j++ }
                (x == "oh" || x == "o") && sb.isNotEmpty() -> { sb.append('0'); j++ }
                (x == "double" || x == "triple") && next != null && next in ONES -> {
                    repeat(if (x == "double") 2 else 3) { sb.append(ONES[next]) }
                    j += 2
                }
                else -> break
            }
        }
        // A trailing "oh"/"o" is more likely an interjection than a zero.
        while (j > start && (w[j - 1] == "oh" || w[j - 1] == "o")) { j--; sb.setLength(sb.length - 1) }
        return if (sb.length >= 3 && j - start >= 2) sb.toString() to j else null
    }

    /** A number from 1 to 99 spoken as one unit: "nineteen", "forty", "forty five". */
    fun parseTwoDigit(w: List<String>, start: Int): Pair<Int, Int>? {
        val x = w.getOrNull(start) ?: return null
        TEENS[x]?.let { return it to start + 1 }
        TENS[x]?.let { t ->
            val o = w.getOrNull(start + 1)?.let { ONES[it] }
            return if (o != null && o > 0) (t + o) to start + 2 else t to start + 1
        }
        return null
    }

    /** "nineteen eighty four" → 1984, "twenty twenty six" → 2026, "nineteen oh five" → 1905. */
    fun parseYear(w: List<String>, start: Int): Pair<Int, Int>? {
        val (century, j) = parseTwoDigit(w, start) ?: return null
        if (century !in 15..20) return null
        val b: Int
        val end: Int
        if ((w.getOrNull(j) == "oh" || w.getOrNull(j) == "o") && (ONES[w.getOrNull(j + 1)] ?: 0) > 0) {
            b = ONES[w[j + 1]]!!; end = j + 2
        } else {
            val (v, e) = parseTwoDigit(w, j) ?: return null
            if (v < 10) return null
            b = v; end = e
        }
        val after = w.getOrNull(end)
        if (after != null && (isNumberWord(after) || after == "hundred")) return null
        return (century * 100 + b) to end
    }

    fun ordinalSuffix(n: Long): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1L -> "st"
        n % 10 == 2L -> "nd"
        n % 10 == 3L -> "rd"
        else -> "th"
    }

    /** 250000 → "2,50,000" (Indian) or "250,000" (international). */
    fun group(v: Long, indian: Boolean): String {
        val neg = v < 0
        val s = kotlin.math.abs(v).toString()
        if (s.length <= 3) return if (neg) "-$s" else s
        val sb = StringBuilder()
        if (indian) {
            val head = s.dropLast(3)
            val firstLen = if (head.length % 2 == 0) 2 else 1
            sb.append(head, 0, firstLen)
            var k = firstLen
            while (k < head.length) { sb.append(',').append(head, k, k + 2); k += 2 }
            sb.append(',').append(s.takeLast(3))
        } else {
            val firstLen = s.length % 3
            if (firstLen > 0) sb.append(s, 0, firstLen)
            var k = firstLen
            while (k < s.length) { if (sb.isNotEmpty()) sb.append(','); sb.append(s, k, k + 3); k += 3 }
        }
        return if (neg) "-$sb" else sb.toString()
    }
}
