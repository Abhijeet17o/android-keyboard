package org.futo.inputmethod.latin.uix.utils.voicepp

import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.ONES
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.WORD_SCALES
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.group
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.ordinalSuffix
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.parseCardinal
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.parseDigitSequence
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.parseTwoDigit
import org.futo.inputmethod.latin.uix.utils.voicepp.NumberWords.parseYear

/**
 * Inverse text normalization: spoken numbers → written form ("twenty five" → "25",
 * "three forty five PM" → "3:45 PM", "two lakh rupees" → "₹2 lakh").
 *
 * Every rule is anchored on the number words themselves, never on a lazy prefix, so a number in the
 * middle of a sentence converts exactly like one at the start. Small numbers (zero to nine) stay
 * spelled out in prose ("no one", "one of the best") unless something numeric is next to them.
 */
internal object NumberNormalizer {
    private val MONTHS = setOf(
        "january", "february", "march", "april", "may", "june", "july",
        "august", "september", "october", "november", "december"
    )
    // "may" and "march" are common words; only treat them as months when Whisper capitalised them.
    private val AMBIGUOUS_MONTHS = setOf("may", "march")

    private val AM = setOf("am", "a.m.", "a.m")
    private val PM = setOf("pm", "p.m.", "p.m")
    private val TIME_PREPOSITIONS = setOf(
        "at", "by", "around", "till", "until", "from", "before", "after", "since", "to", "between"
    )

    // Words before a number that make it a label: "page 3", "step 2", "version 4".
    private val NUMBER_PREFIX = setOf(
        "page", "pages", "step", "number", "no.", "chapter", "version", "room", "level", "gate",
        "platform", "class", "grade", "option", "section", "part", "figure", "table", "item",
        "line", "verse", "episode", "season", "rank", "size", "floor", "sector", "block", "phase",
        "round", "ward", "plot", "flat", "bus", "route", "seat", "row", "exit", "lane", "channel",
        "volume", "slide", "question", "unit", "module", "lesson", "standard", "std", "iphone",
        "windows", "android", "ios", "galaxy", "pixel", "python", "java", "plus", "minus", "times",
        "divided", "equals", "rs", "rs.", "rupees", "inr", "usd"
    )

    private val UNIT_ABBREVIATIONS = mapOf(
        "kilometers" to "km", "kilometres" to "km", "kilometer" to "km", "kilometre" to "km", "km" to "km",
        "kilograms" to "kg", "kilogram" to "kg", "kgs" to "kg", "kg" to "kg", "kilo" to "kg", "kilos" to "kg",
        "centimeters" to "cm", "centimetres" to "cm", "centimeter" to "cm", "centimetre" to "cm",
        "millimeters" to "mm", "millimetres" to "mm", "millimeter" to "mm", "millimetre" to "mm",
        "milligrams" to "mg", "milligram" to "mg", "milliliters" to "ml", "millilitres" to "ml",
        "gigabytes" to "GB", "gigabyte" to "GB", "gb" to "GB", "gigs" to "GB",
        "megabytes" to "MB", "megabyte" to "MB", "mb" to "MB",
        "terabytes" to "TB", "terabyte" to "TB", "tb" to "TB",
        "kilobytes" to "KB", "kilobyte" to "KB", "kb" to "KB",
        "gigahertz" to "GHz", "megahertz" to "MHz", "mph" to "mph", "kmph" to "km/h",
        "ounces" to "oz", "ounce" to "oz", "mah" to "mAh",
    )
    // Units that force digits but read better as words.
    private val UNIT_WORDS = setOf(
        "meters", "metres", "meter", "metre", "miles", "mile", "feet", "inches",
        "liters", "litres", "liter", "litre", "grams", "gram", "tonnes", "tons", "acres",
        "degrees", "degree", "pounds", "lbs", "volts", "watts", "calories",
        "megapixels", "rpm", "bhk"
    )

    private val CURRENCY_AFTER = setOf(
        "dollars", "dollar", "bucks", "buck", "rupees", "rupee", "rs", "rs.", "euros", "euro",
        "pounds", "pound", "yen", "inr", "usd"
    )
    private val CURRENCY_BEFORE = setOf("rs", "rs.", "rupees", "inr", "₹", "$", "usd")
    private val SUBUNITS = setOf("cents", "cent", "paise", "paisa", "pence", "p")

    private val NUMBER_TOKEN = Regex("""^-?\d+(?:,\d+)*(?:\.\d+)?$""")
    private val COMPACT_TIME = Regex("""^(\d{1,2})(?:[.:](\d{2}))?(am|pm|a\.m\.|p\.m\.)$""", RegexOption.IGNORE_CASE)
    private val COMPACT_RUPEES = Regex("""^(?:rs\.?|₹)(\d[\d,]*(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
    private val CLOCK_TIME = Regex("""^\d{1,2}[.:]\d{2}$""")

    fun isNumberToken(t: Token?) = t != null && t.isWord && NUMBER_TOKEN.matches(t.text)

    private class Match(val end: Int, val replacement: List<Token>)

    fun apply(tokens: MutableList<Token>, ctx: Ctx) {
        expandHyphenatedNumbers(tokens)
        val w = tokens.mapTo(ArrayList()) { if (it.isWord && !it.locked) it.lower else "\u0000" }

        var i = 0
        while (i < tokens.size) {
            val m = if (w[i] == "\u0000") null else matchAt(tokens, w, i, ctx)
            if (m == null) { i++; continue }
            for (k in i until m.end) { tokens.removeAt(i); w.removeAt(i) }
            m.replacement.forEachIndexed { k, t -> tokens.add(i + k, t); w.add(i + k, "\u0000") }
            i += m.replacement.size
        }

        attachUnits(tokens, ctx)
    }

    // "forty-five" → "forty" "five" so the grammar can see the parts.
    private fun expandHyphenatedNumbers(tokens: MutableList<Token>) {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isWord && !t.locked && '-' in t.text) {
                val parts = t.lower.split('-')
                if (parts.size > 1 && parts.all { NumberWords.isNumberWord(it) }) {
                    tokens.removeAt(i)
                    parts.forEachIndexed { k, p -> tokens.add(i + k, Token.word(p)) }
                    i += parts.size
                    continue
                }
            }
            i++
        }
    }

    private fun matchAt(tokens: List<Token>, w: List<String>, i: Int, ctx: Ctx): Match? {
        val x = w[i]
        val prev = w.getOrNull(i - 1)

        if (x in MONTHS && (x !in AMBIGUOUS_MONTHS || tokens[i].text[0].isUpperCase())) {
            matchDate(tokens, w, i, ctx)?.let { return it }
        }
        matchTime(w, i, prev)?.let { return it }

        if ((x == "minus" || x == "negative") && !isNumberToken(tokens.getOrNull(i - 1))) {
            val card = parseCardinal(w, i + 1)
            if (card != null && !card.ordinal) {
                val (text, end) = withDecimal(w, card)
                return Match(end, listOf(Token.word("-$text")))
            }
        }

        parseDigitSequence(w, i)?.let { (digits, end) ->
            return Match(end, listOf(Token.word(formatDigitString(digits, ctx))))
        }

        parseYear(w, i)?.let { (year, end) ->
            val after = w.getOrNull(end)
            if (after == null || (after !in UNIT_WORDS && after !in UNIT_ABBREVIATIONS && after != "percent" &&
                        after !in DURATIONS)) {
                return Match(end, listOf(Token.word(year.toString())))
            }
        }

        val card = parseCardinal(w, i) ?: return null
        // "ten fifteen", "twenty five thirty": several numbers that are neither one number nor a time.
        // Leave the whole run spelled out instead of producing "10 15".
        if (!card.ordinal && w.getOrNull(card.end).let { it != null && NumberWords.isCardinalWord(it) }) {
            var runEnd = card.end
            while (runEnd < w.size && NumberWords.isNumberWord(w[runEnd])) runEnd++
            return Match(runEnd, tokens.subList(i, runEnd).toList())
        }
        if (card.ordinal) {
            val next = w.getOrNull(card.end)
            val dateContext = card.value in 1..31 &&
                    (next in MONTHS || (next == "of" && w.getOrNull(card.end + 1) in MONTHS))
            if (card.value < 10 && !dateContext) return null
            return Match(card.end, listOf(Token.word("${card.value}${ordinalSuffix(card.value)}")))
        }

        val (decimalText, end) = withDecimal(w, card)
        val isDecimal = end != card.end
        val singleSmallWord = !isDecimal && card.value < 10 && card.end - i == 1
        if (singleSmallWord && !isNumericContext(tokens, w, i, card.end)) return null
        if (isDecimal) return Match(end, listOf(Token.word(decimalText)))

        if (card.scaleCount == 1 && card.lastWord in WORD_SCALES) {
            val scale = NumberWords.SCALES[card.lastWord]!!
            return Match(card.end, listOf(Token.word((card.value / scale).toString()), Token.word(card.lastWord)))
        }
        if (prev == "year" && card.value in 1000..2999) return Match(card.end, listOf(Token.word(card.value.toString())))
        return Match(card.end, listOf(Token.word(formatInteger(card.value, card.hasHundred, ctx))))
    }

    private val DURATIONS = setOf(
        "seconds", "minutes", "hours", "days", "weeks", "months", "years", "people", "times", "percent"
    )

    private fun formatInteger(v: Long, hasHundred: Boolean, ctx: Ctx): String = when {
        // "two thousand twenty six" reads as a year; "two thousand" and "fifteen hundred" read as amounts.
        v in 1000..9999 && !hasHundred && v % 1000 != 0L -> v.toString()
        else -> group(v, ctx.indian)
    }

    private fun withDecimal(w: List<String>, card: NumberWords.Cardinal): Pair<String, Int> {
        if (card.ordinal || w.getOrNull(card.end) != "point") return card.value.toString() to card.end
        val sb = StringBuilder()
        var j = card.end + 1
        while (j < w.size && (w[j] in ONES || (w[j] == "oh" && sb.isNotEmpty()))) {
            sb.append(ONES[w[j]] ?: 0)
            j++
        }
        return if (sb.isEmpty()) card.value.toString() to card.end else "${card.value}.$sb" to j
    }

    private fun formatDigitString(d: String, ctx: Ctx): String = when {
        d.length == 10 && ctx.indian -> "${d.substring(0, 5)} ${d.substring(5)}"
        d.length == 10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}"
        d.length == 7 && !ctx.indian -> "${d.substring(0, 3)}-${d.substring(3)}"
        else -> d
    }

    private fun isNumericContext(tokens: List<Token>, w: List<String>, start: Int, end: Int): Boolean {
        val next = w.getOrNull(end)
        val prev = w.getOrNull(start - 1)
        if (next != null && (next in UNIT_ABBREVIATIONS || next in UNIT_WORDS || next in CURRENCY_AFTER ||
                    next in WORD_SCALES || next == "percent" || next in AM || next in PM ||
                    next == "o'clock" || next == "oclock" ||
                    (next == "point" && w.getOrNull(end + 1) in ONES))) return true
        if (prev != null && (prev in NUMBER_PREFIX || prev in CURRENCY_BEFORE)) return true
        if (isNumberToken(tokens.getOrNull(start - 1)) || isNumberToken(tokens.getOrNull(end))) return true
        if (tokens.getOrNull(end)?.text == "%" ) return true
        // The whole utterance is just the number.
        return tokens.indices.all { it in start until end || tokens[it].isPunct }
    }

    private fun hourAt(w: List<String>, i: Int, allowMilitary: Boolean): Pair<Int, Int>? {
        val x = w.getOrNull(i) ?: return null
        ONES[x]?.let { return if (it in 1..9) it to i + 1 else null }
        if (x == "ten") return 10 to i + 1
        if (x == "eleven") return 11 to i + 1
        if (x == "twelve") return 12 to i + 1
        if (allowMilitary) parseTwoDigit(w, i)?.let { (v, e) -> if (v in 13..23) return v to e }
        return null
    }

    private fun meridiemAt(w: List<String>, j: Int): String? = when (w.getOrNull(j)) {
        in AM -> "AM"
        in PM -> "PM"
        else -> null
    }

    private fun timeTokens(text: String, meridiem: String?): List<Token> =
        if (meridiem == null) listOf(Token.word(text)) else listOf(Token.word(text), Token.word(meridiem))

    private fun matchTime(w: List<String>, i: Int, prev: String?): Match? {
        val x = w[i]
        // "half past three", "quarter past five", "quarter to six"
        if ((x == "half" || x == "quarter") && (w.getOrNull(i + 1) == "past" || (x == "quarter" && w.getOrNull(i + 1) == "to"))) {
            val (h, e) = hourAt(w, i + 2, allowMilitary = false) ?: return null
            val text = when {
                x == "half" -> "$h:30"
                w[i + 1] == "past" -> "$h:15"
                else -> "${if (h == 1) 12 else h - 1}:45"
            }
            val mer = meridiemAt(w, e)
            return Match(if (mer != null) e + 1 else e, timeTokens(text, mer))
        }

        val atContext = prev in TIME_PREPOSITIONS
        val (h, j) = hourAt(w, i, allowMilitary = atContext) ?: return null
        var minutes: Int? = null
        var k = j
        val nextOne = w.getOrNull(j + 1)?.let { ONES[it] }
        if ((w.getOrNull(j) == "oh" || w.getOrNull(j) == "o") && nextOne != null && nextOne > 0) {
            minutes = nextOne; k = j + 2
        } else {
            val two = parseTwoDigit(w, j)
            if (two != null && two.first in 10..59) {
                minutes = two.first; k = two.second
            } else {
                val one = w.getOrNull(j)?.let { ONES[it] }
                if (one != null && one in 1..9 && meridiemAt(w, j + 1) != null) { minutes = one; k = j + 1 }
            }
        }
        // "three forty five thousand" is a number, not a time.
        if (w.getOrNull(k) == "hundred" || w.getOrNull(k) in NumberWords.SCALES) return null

        val mer = meridiemAt(w, k)
        val clock = if (minutes != null) "$h:${minutes.toString().padStart(2, '0')}" else "$h"
        return when {
            mer != null && h <= 12 -> Match(k + 1, timeTokens(clock, mer))
            minutes == null && (w.getOrNull(k) == "o'clock" || w.getOrNull(k) == "oclock") ->
                Match(k + 1, listOf(Token.word("$h"), Token.word("o'clock")))
            minutes == null && w.getOrNull(k) == "o" && w.getOrNull(k + 1) == "clock" ->
                Match(k + 2, listOf(Token.word("$h"), Token.word("o'clock")))
            minutes != null && atContext -> Match(k, timeTokens(clock, null))
            else -> null
        }
    }

    private fun matchDate(tokens: List<Token>, w: List<String>, i: Int, ctx: Ctx): Match? {
        val month = Token.word(w[i].replaceFirstChar { it.uppercaseChar() })
        parseYear(w, i + 1)?.let { (year, end) ->
            return Match(end, listOf(month, Token.word(year.toString())))
        }
        val day = parseCardinal(w, i + 1) ?: return null
        if (day.value !in 1..31 || day.scaleCount > 0 || day.hasHundred) return null
        val dayText = if (day.ordinal) "${day.value}${ordinalSuffix(day.value)}" else day.value.toString()

        var j = day.end
        if (tokens.getOrNull(j)?.text == ",") j++
        val year: Pair<String, Int>? = parseYear(w, j)?.let { it.first.toString() to it.second }
            ?: parseCardinal(w, j)?.takeIf { it.value in 1000..2999 && !it.ordinal }
                ?.let { formatInteger(it.value, it.hasHundred, ctx) to it.end }
            ?: tokens.getOrNull(j)?.takeIf { Regex("""^\d{4}$""").matches(it.text) }?.let { it.text to j + 1 }

        return if (year != null) {
            Match(year.second, listOf(month, Token.word(dayText), Token.punct(","), Token.word(year.first)))
        } else {
            Match(day.end, listOf(month, Token.word(dayText)))
        }
    }

    private fun currencySymbol(word: String, next: String?, ctx: Ctx): String? = when (word) {
        "dollars", "dollar", "usd" -> "$"
        "bucks", "buck" -> if (ctx.indian) "₹" else "$"
        "rupees", "rupee", "rs", "rs.", "inr" -> "₹"
        "euros", "euro" -> "€"
        "yen" -> "¥"
        "pounds", "pound" -> if (next == "sterling" || ctx.british) "£" else null
        else -> null
    }

    private fun regroupForCurrency(text: String, ctx: Ctx): String =
        if (Regex("""^\d{5,}$""").matches(text)) group(text.toLong(), ctx.indian) else text

    /** Joins numbers with what follows them: %, currency, units, AM/PM. Works on Whisper's own digits too. */
    private fun attachUnits(tokens: MutableList<Token>, ctx: Ctx) {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isWord && !t.locked) {
                COMPACT_TIME.matchEntire(t.text)?.let { m ->
                    val mins = m.groupValues[2]
                    t.text = if (mins.isEmpty()) m.groupValues[1] else "${m.groupValues[1]}:$mins"
                    val mer = if (m.groupValues[3].lowercase().startsWith("a")) "AM" else "PM"
                    tokens.add(i + 1, Token.word(mer))
                }
                if (ctx.indian) COMPACT_RUPEES.matchEntire(t.text)?.let { m ->
                    t.text = "₹" + regroupForCurrency(m.groupValues[1], ctx)
                }
            }
            if (!isNumberToken(t)) { i++; continue }

            fun lowerAt(k: Int) = tokens.getOrNull(k)?.takeIf { it.isWord }?.lower
            val n1 = lowerAt(i + 1)
            val n2 = lowerAt(i + 2)
            val p1 = tokens.getOrNull(i - 1)

            // Currency word before the number: "rupees five hundred", "Rs. 500"
            if (p1 != null && p1.isWord && p1.lower in CURRENCY_BEFORE && p1.lower != "usd") {
                val sym = if (p1.text == "$") "$" else "₹"
                t.text = sym + regroupForCurrency(t.text, ctx)
                tokens.removeAt(i - 1)
                i--
                continue
            }

            when {
                n1 == "percent" -> { t.text += "%"; tokens.removeAt(i + 1) }
                n1 == "per" && n2 == "cent" -> { t.text += "%"; tokens.removeAt(i + 1); tokens.removeAt(i + 1) }
                n1 != null && n1 in WORD_SCALES && n2 != null && currencySymbol(n2, lowerAt(i + 3), ctx) != null -> {
                    t.text = currencySymbol(n2, lowerAt(i + 3), ctx) + t.text
                    tokens.removeAt(i + 2)
                    if (lowerAt(i + 2) == "sterling") tokens.removeAt(i + 2)
                }
                n1 != null && currencySymbol(n1, n2, ctx) != null -> {
                    t.text = currencySymbol(n1, n2, ctx) + regroupForCurrency(t.text, ctx)
                    tokens.removeAt(i + 1)
                    if (lowerAt(i + 1) == "sterling") tokens.removeAt(i + 1)
                    // "... and ninety nine cents" → "$49.99"
                    if (lowerAt(i + 1) == "and" && isNumberToken(tokens.getOrNull(i + 2)) &&
                        lowerAt(i + 3) in SUBUNITS && '.' !in t.text) {
                        val sub = tokens[i + 2].text.toIntOrNull()
                        if (sub != null && sub in 0..99) {
                            t.text += "." + sub.toString().padStart(2, '0')
                            repeat(3) { tokens.removeAt(i + 1) }
                        }
                    }
                }
                n1 == "degrees" || n1 == "degree" -> {
                    tokens.removeAt(i + 1)
                    when (lowerAt(i + 1)) {
                        "celsius", "centigrade" -> { t.text += "°C"; tokens.removeAt(i + 1) }
                        "fahrenheit" -> { t.text += "°F"; tokens.removeAt(i + 1) }
                        else -> t.text += "°"
                    }
                }
                n1 != null && n1 in UNIT_ABBREVIATIONS -> {
                    tokens[i + 1].text = UNIT_ABBREVIATIONS[n1]!!
                    if (tokens[i + 1].text == "km" && lowerAt(i + 2) == "per" && lowerAt(i + 3) == "hour") {
                        tokens[i + 1].text = "km/h"; tokens.removeAt(i + 2); tokens.removeAt(i + 2)
                    }
                }
                n1 != null && (n1 in AM || n1 in PM) && (CLOCK_TIME.matches(t.text) || t.text.toIntOrNull() in 1..12) -> {
                    t.text = t.text.replace('.', ':')
                    val mer = tokens[i + 1]
                    // "… at 3.30 p.m." — Whisper's abbreviation dot doubles as the full stop.
                    val after = tokens.getOrNull(i + 2)
                    val endsSentence = mer.text.endsWith(".") && (after == null || after.isNewline ||
                            (after.isWord && after.text[0].isUpperCase()))
                    mer.text = if (n1 in AM) "AM" else "PM"
                    if (endsSentence) tokens.add(i + 2, Token.punct("."))
                }
            }
            i++
        }
    }
}
