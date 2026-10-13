package org.futo.inputmethod.latin.uix.utils.voicepp

import java.text.Normalizer

/**
 * Hinglish support (opt-in): Devanagari → Latin script for mixed Hindi-English dictation, and
 * romanized Hindi amounts ("paanch sau rupaye" → "₹500", "dedh lakh" → "1.5 lakh", "teen baje" → "3 baje").
 */
internal object Hinglish {
    // ---------------------------------------------------------------- transliteration

    private val COMMON_WORDS = mapOf(
        "है" to "hai", "हैं" to "hain", "में" to "mein", "मैं" to "main", "नहीं" to "nahi", "क्या" to "kya",
        "कि" to "ki", "की" to "ki", "के" to "ke", "का" to "ka", "को" to "ko", "से" to "se", "पर" to "par",
        "भी" to "bhi", "तो" to "toh", "हाँ" to "haan", "हां" to "haan", "ना" to "na", "और" to "aur",
        "यह" to "yeh", "वह" to "woh", "ये" to "ye", "वो" to "wo", "कल" to "kal", "आज" to "aaj",
        "अभी" to "abhi", "ठीक" to "theek", "हूँ" to "hoon", "हूं" to "hoon", "हो" to "ho", "था" to "tha",
        "थी" to "thi", "थे" to "the", "रहा" to "raha", "रही" to "rahi", "रहे" to "rahe", "गया" to "gaya",
        "गई" to "gayi", "गए" to "gaye", "करो" to "karo", "करना" to "karna", "कर" to "kar", "जी" to "ji",
        "बहुत" to "bahut", "अच्छा" to "accha", "मुझे" to "mujhe", "तुम" to "tum", "आप" to "aap",
        "हम" to "hum", "मेरा" to "mera", "मेरी" to "meri", "मेरे" to "mere", "तेरा" to "tera",
        "उसका" to "uska", "लेकिन" to "lekin", "क्योंकि" to "kyunki", "कैसे" to "kaise", "कहाँ" to "kahan",
        "कब" to "kab", "क्यों" to "kyun", "एक" to "ek", "दो" to "do", "तीन" to "teen", "चार" to "chaar",
        "पांच" to "paanch", "पाँच" to "paanch", "सौ" to "sau", "हज़ार" to "hazaar", "हजार" to "hazaar",
        "लाख" to "lakh", "करोड़" to "crore", "रुपये" to "rupaye", "रुपए" to "rupaye", "बजे" to "baje",
        "यार" to "yaar", "चलो" to "chalo", "बात" to "baat", "काम" to "kaam", "घर" to "ghar",
        "समय" to "samay", "मैंने" to "maine", "उसने" to "usne", "हमें" to "humein", "उन्हें" to "unhe",
        "सकता" to "sakta", "सकती" to "sakti", "चाहिए" to "chahiye", "वाला" to "wala", "वाली" to "wali",
    )

    private val INDEPENDENT_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )
    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh",
        'ज' to "j", 'झ' to "jh", 'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh",
        'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p",
        'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l",
        'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
    )
    private val NUKTA_FORMS = mapOf('क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "d", 'ढ' to "rh", 'फ' to "f")
    private val MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e",
        'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॅ' to "e", 'ॉ' to "o"
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    private fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

    // Nukta letters (क़ ज़ ड़ …) are excluded from NFC composition, so NFC leaves them as
    // consonant + nukta. Normalizing both the input and the word table makes lookups agree.
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
    private val commonWords by lazy { COMMON_WORDS.mapKeys { nfc(it.key) } }

    /** Replaces each run of Devanagari with a Latin-script spelling; Latin text is left as is. */
    fun transliterate(input: String): String {
        if (input.none(::isDevanagari)) return input
        val text = nfc(input)
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!isDevanagari(text[i])) { sb.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && isDevanagari(text[j]) && text[j] != '।' && text[j] != '॥') j++
            if (j == i) { sb.append('.'); i++; continue } // danda
            val word = text.substring(i, j)
            sb.append(commonWords[word] ?: transliterateWord(word))
            i = j
        }
        return sb.toString()
    }

    private class Syllable(val consonant: String?, var vowel: String?, val inherent: Boolean)

    private fun transliterateWord(word: String): String {
        val syl = ArrayList<Syllable>()
        var i = 0
        val tail = StringBuilder()
        while (i < word.length) {
            val c = word[i]
            when {
                c in CONSONANTS -> {
                    var cons = CONSONANTS[c]!!
                    if (word.getOrNull(i + 1) == NUKTA) { cons = NUKTA_FORMS[c] ?: cons; i++ }
                    val next = word.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> { syl.add(Syllable(cons, null, false)); i++ }
                        next != null && next in MATRAS -> { syl.add(Syllable(cons, MATRAS[next], false)); i++ }
                        else -> syl.add(Syllable(cons, "a", true))
                    }
                }
                c in INDEPENDENT_VOWELS -> syl.add(Syllable(null, INDEPENDENT_VOWELS[c], false))
                c == 'ं' || c == 'ँ' -> syl.lastOrNull()?.let { it.vowel = (it.vowel ?: "") + "n" } ?: tail.append('n')
                c == 'ः' -> syl.lastOrNull()?.let { it.vowel = (it.vowel ?: "") + "h" }
                c in '०'..'९' -> tail.append('0' + (c - '०'))
                else -> {}
            }
            i++
        }
        // Schwa deletion: drop the inherent "a" at the end of a word, and in the middle when it sits
        // between a vowel and a consonant that carries its own vowel ("samajhna", "kamla").
        for (k in syl.indices) {
            val s = syl[k]
            if (!s.inherent || syl.size == 1) continue
            val isLast = k == syl.size - 1
            val prevHasVowel = k > 0 && syl[k - 1].vowel != null
            val next = syl.getOrNull(k + 1)
            val nextIsFull = next != null && next.consonant != null && next.vowel != null && k + 1 != syl.size - 1
            if (isLast || (prevHasVowel && nextIsFull && !syl[k - 1].inherent)) s.vowel = null
        }
        // Romanized Hindi ends words in a short "a": karna, bada, gaya (not karnaa).
        syl.lastOrNull()?.let { if (syl.size > 1 && it.consonant != null && it.vowel == "aa") it.vowel = "a" }
        val sb = StringBuilder()
        for (s in syl) { s.consonant?.let(sb::append); s.vowel?.let(sb::append) }
        return sb.append(tail).toString()
    }

    // ---------------------------------------------------------------- numbers

    private val UNITS = mapOf(
        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0, "chaar" to 4.0, "paanch" to 5.0,
        "panch" to 5.0, "chhe" to 6.0, "chhah" to 6.0, "saat" to 7.0, "aath" to 8.0, "nau" to 9.0,
        "das" to 10.0, "gyarah" to 11.0, "barah" to 12.0, "terah" to 13.0, "chaudah" to 14.0,
        "pandrah" to 15.0, "solah" to 16.0, "satrah" to 17.0, "atharah" to 18.0, "unnis" to 19.0,
        "bees" to 20.0, "pachees" to 25.0, "tees" to 30.0, "chalis" to 40.0, "chaalis" to 40.0,
        "pachaas" to 50.0, "pachas" to 50.0, "saath" to 60.0, "sattar" to 70.0, "assi" to 80.0,
        "nabbe" to 90.0, "dedh" to 1.5, "dhai" to 2.5, "dhaai" to 2.5,
    )
    private val SCALES = mapOf("sau" to 100L, "hazaar" to 1_000L, "hazar" to 1_000L, "hajar" to 1_000L)
    private val BIG_SCALES = mapOf("lakh" to "lakh", "lac" to "lakh", "crore" to "crore", "karod" to "crore", "karor" to "crore")
    private val TRIGGERS = setOf("rupaye", "rupay", "rupaiye", "rupees", "rupee", "rs", "baje", "percent")

    /** Converts romanized Hindi amounts; only when a scale, currency or "baje" follows (so English "do" is safe). */
    fun numbers(tokens: MutableList<Token>, ctx: Ctx) {
        var i = 0
        while (i < tokens.size) {
            val unit = tokens[i].takeIf { it.isWord && !it.locked }?.lower?.let { UNITS[it] }
            if (unit == null) { i++; continue }

            var total = 0.0
            var group: Double = unit
            var j = i + 1
            var sawScale = false
            while (j < tokens.size && tokens[j].isWord) {
                val w = tokens[j].lower
                val s = SCALES[w]
                val u = UNITS[w]
                when {
                    s != null -> { total += group * s; group = 0.0; sawScale = true }
                    u != null && group == 0.0 -> group = u
                    else -> break
                }
                j++
            }
            total += group
            val next = tokens.getOrNull(j)?.lower
            val big = next?.let { BIG_SCALES[it] }
            if (!sawScale && big == null && next !in TRIGGERS) { i++; continue }

            val digits = if (total % 1.0 == 0.0) NumberWords.group(total.toLong(), ctx.indian) else total.toString()
            for (k in i until j) tokens.removeAt(i)
            tokens.add(i, Token.word(digits))
            if (big != null) tokens[i + 1].text = big
            if (tokens.getOrNull(i + 1 + (if (big != null) 1 else 0))?.lower in setOf("rupaye", "rupay", "rupaiye")) {
                tokens[i + 1 + (if (big != null) 1 else 0)].text = "rupees"
            }
            i++
        }
    }
}
