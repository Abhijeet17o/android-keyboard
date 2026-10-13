package org.futo.inputmethod.latin.uix.utils.voicepp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoicePPGoldenTest {
    data class Case(val locale: Locale, val input: String, val expected: String, val tag: String, val line: Int)

    private fun unescape(s: String) = s.replace("\\n", "\n")

    private val cases: List<Case> by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("voicepp_golden.txt")
            ?: error("voicepp_golden.txt not on the test classpath")
        stream.bufferedReader(Charsets.UTF_8).readLines().mapIndexedNotNull { idx, line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@mapIndexedNotNull null
            val parts = line.split(" | ")
            require(parts.size == 4) { "voicepp_golden.txt:${idx + 1}: expected 4 columns, got ${parts.size}" }
            Case(Locale.forLanguageTag(parts[0].trim()), unescape(parts[1]), unescape(parts[2]), parts[3].trim(), idx + 1)
        }
    }

    private fun run(input: String, locale: Locale) = VoicePipeline.process(input, VoicePPOptions(locale = locale))

    @Test
    fun goldenCases() {
        val failures = cases.mapNotNull { c ->
            val actual = run(c.input, c.locale)
            if (actual == c.expected) null
            else "  line ${c.line} [${c.tag}] ${c.input.show()}\n      expected ${c.expected.show()}\n      actual   ${actual.show()}"
        }
        val passed = cases.size - failures.size
        println("VoicePP golden: $passed/${cases.size} passed")
        assertTrue("${failures.size} golden case(s) failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun outputIsIdempotent() {
        val failures = cases.mapNotNull { c ->
            val once = run(c.input, c.locale)
            val twice = run(once, c.locale)
            if (once == twice) null else "  line ${c.line}: ${once.show()} → ${twice.show()}"
        }
        assertTrue("Re-processing changed the output:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun outputHasNoSpacingDefects() {
        val defects = listOf(Regex("  "), Regex(" [,.!?;:%]"), Regex("[,;]{2}"), Regex("\\d[A-Za-z]{2,}"))
        val failures = cases.flatMap { c ->
            val out = run(c.input, c.locale)
            defects.filter { it.containsMatchIn(out) && !it.containsMatchIn(c.expected) }
                .map { "  line ${c.line}: /${it.pattern}/ in ${out.show()}" }
        }
        assertTrue("Spacing defects:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun verbalTicsAreOptIn() {
        val options = VoicePPOptions(removeVerbalTics = true)
        assertEquals("I was like, really tired.", VoicePipeline.process("I was like, you know, really tired.", options))
        assertEquals("We should go.", VoicePipeline.process("Basically, we should go.", options))
        assertEquals("Do you know him?", VoicePipeline.process("Do you know him?", options))
        assertEquals("What I mean is simple.", VoicePipeline.process("What I mean is simple.", options))
    }

    @Test
    fun smartParagraphsAreOptIn() {
        val text = "We shipped it. Users liked it. Moving on, the budget is fine."
        assertEquals(text, VoicePipeline.process(text))
        assertEquals(
            "We shipped it. Users liked it.\n\nMoving on, the budget is fine.",
            VoicePipeline.process(text, VoicePPOptions(smartParagraphs = true))
        )
    }

    @Test
    fun partialResultsSkipNumberFormatting() {
        val options = VoicePPOptions(partial = true)
        assertEquals("I have twenty five, um", VoicePipeline.process("I have twenty five, um", VoicePPOptions(partial = true, removeFillers = false)))
        assertEquals("Hello, world", VoicePipeline.process("um hello comma world", options))
    }

    @Test
    fun personalVocabulary() {
        val options = VoicePPOptions(vocabulary = listOf("GitHub", "Abhijeet", "FUTO", "iPhone", "Wispr", "US", "Kubernetes"))
        fun p(s: String) = VoicePipeline.process(s, options)
        assertEquals("Push it to GitHub.", p("Push it to github."))
        assertEquals("Push it to GitHub.", p("Push it to Git Hub."))
        assertEquals("Ask Abhijeet about it.", p("Ask Abhi jeet about it."))
        assertEquals("Ask Abhijeet about it.", p("Ask Abhijit about it."))
        assertEquals("iPhone is charging.", p("iphone is charging."))
        assertEquals("FUTO keyboard works offline.", p("Futo keyboard works offline."))
        assertEquals("It is better than Wispr Flow.", p("It is better than Whisper Flow.".replace("Whisper", "Wisper")))
        // Lowercase common words are never fuzzily rewritten, and short acronyms don't hijack words.
        assertEquals("Tell us what you think.", p("Tell us what you think."))
        assertEquals("The whisper was quiet.", p("The whisper was quiet."))
        assertEquals("Deploy on Kubernetes today.", p("Deploy on kubernetes today."))
    }

    @Test
    fun hallucinationLoopsCollapse() {
        assertEquals("I'm going to go.", VoicePipeline.process("I'm going to go. I'm going to go. I'm going to go. I'm going to go."))
        assertEquals("Thanks", VoicePipeline.process("Thanks Thanks Thanks Thanks Thanks Thanks"))
        assertEquals("Very, very good.", VoicePipeline.process("Very, very good."))
        assertEquals("No no no, not that.", VoicePipeline.process("No no no, not that."))
        assertEquals("See you tomorrow.", VoicePipeline.process("See you tomorrow. Subtitles by the Amara.org community"))
    }

    @Test
    fun hinglish() {
        val options = VoicePPOptions(locale = Locale.forLanguageTag("en-IN"), hinglish = true)
        fun p(s: String) = VoicePipeline.process(s, options)
        assertEquals("Mujhe ₹500 chahiye", p("मुझे पाँच सौ रुपये चाहिए"))
        assertEquals("Kal meeting hai", p("कल मीटिंग है"))
        assertEquals("Meeting 3 baje hai", p("meeting teen baje hai"))
        assertEquals("₹2 lakh", p("do lakh rupaye"))
        assertEquals("Budget 1.5 lakh hai", p("budget dedh lakh hai"))
        assertEquals("5,500 log aaye", p("paanch hazaar paanch sau log aaye"))
        // English words that are also Hindi numbers stay put without a scale/currency after them.
        assertEquals("I will do it", p("I will do it"))
        assertEquals("The teen was late", p("the teen was late"))
        // Mixed scripts: only the Devanagari is transliterated.
        val mixed = p("Please मेरा email check करो")
        assertEquals("Please mera email check karo", mixed)
        assertTrue(p("मैं आज बहुत खुश हूँ").none { it in 'ऀ'..'ॿ' })
        // Output of a Hinglish model (Latin script, mixed languages, phonetic spellings).
        assertEquals("Yeh kaam zaroori hai isliye main office ja raha hoon",
            p("yah kaam jaroori hai islie main office ja raha hoon"))
        assertEquals("Kal ki meeting 4 baje hai, please calendar check kar lo",
            p("kal ki meeting char baje hai, please calendar check kar lo"))
        // Off by default.
        assertEquals("कल मीटिंग है", VoicePipeline.process("कल मीटिंग है"))
    }

    @Test
    fun britishPounds() {
        assertEquals("It costs £20", VoicePipeline.process("it costs twenty pounds", VoicePPOptions(locale = Locale.UK)))
        assertEquals("It weighs 20 pounds", VoicePipeline.process("it weighs twenty pounds", VoicePPOptions(locale = Locale.US)))
    }

    @Test
    fun typicalSentenceIsFast() {
        val input = "um so the meeting is on december twenty fifth at three forty five PM and it costs " +
                "two lakh fifty thousand rupees comma my email is john at gmail dot com period"
        val options = VoicePPOptions(locale = Locale.forLanguageTag("en-IN"))
        repeat(200) { VoicePipeline.process(input, options) } // warm up the JIT
        val n = 500
        val start = System.nanoTime()
        repeat(n) { VoicePipeline.process(input, options) }
        val perCallMs = (System.nanoTime() - start) / 1e6 / n
        println("VoicePP: %.3f ms per sentence".format(perCallMs))
        assertTrue("Too slow: $perCallMs ms", perCallMs < 5.0)
    }

    private fun String.show() = "\"" + replace("\n", "\\n") + "\""
}
