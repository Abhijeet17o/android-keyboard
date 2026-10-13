package org.futo.inputmethod.latin.uix.utils.voicepp

/**
 * Spoken technical text: "main dot py" → main.py, "my underscore var" → my_var,
 * "https colon slash slash" → https://, "john at the rate gmail dot com" → john@gmail.com.
 */
internal object TechTerms {
    private val EXTENSIONS_AND_TLDS = setOf(
        "txt", "py", "js", "ts", "tsx", "jsx", "kt", "kts", "java", "swift", "cpp", "c", "h", "rs", "go",
        "rb", "php", "json", "xml", "html", "htm", "css", "scss", "md", "pdf", "png", "jpg", "jpeg", "gif",
        "svg", "webp", "mp4", "mp3", "wav", "zip", "tar", "gz", "csv", "xlsx", "docx", "pptx", "sql", "db",
        "sh", "bat", "ps1", "gradle", "toml", "yaml", "yml", "env", "apk", "exe", "ipynb", "lock", "log",
        "com", "org", "net", "io", "dev", "app", "co", "gov", "edu", "tech", "ai", "xyz", "in", "uk",
        "me", "info", "us", "ca", "au", "de", "ac", "nic", "res"
    )
    private val TLDS = setOf(
        "com", "org", "net", "io", "dev", "app", "co", "gov", "edu", "tech", "ai", "xyz", "in", "uk",
        "me", "info", "us", "ca", "au", "de"
    )
    private val EMAIL_DOMAINS = setOf(
        "gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "icloud.com", "live.com",
        "proton.me", "protonmail.com", "rediffmail.com", "me.com", "aol.com", "zoho.com"
    )
    private val EMAIL_CUES = setOf("email", "e-mail", "mail", "id", "gmail", "address", "mailid")
    private val NOT_A_NAME = setOf("a", "an", "the", "this", "that", "my", "your")

    fun apply(tokens: MutableList<Token>) {
        protocols(tokens)
        joinDots(tokens)
        joinInfix(tokens, listOf("underscore"), "_")
        joinInfix(tokens, listOf("forward", "slash"), "/")
        joinInfix(tokens, listOf("slash"), "/")
        joinInfix(tokens, listOf("backslash"), "\\")
        joinInfix(tokens, listOf("back", "slash"), "\\")
        joinInfix(tokens, listOf("hyphen"), "-")
        emails(tokens)
    }

    private fun Token?.isPlainWord() = this != null && isWord && !locked

    private fun wordsAt(tokens: List<Token>, i: Int, words: List<String>): Boolean =
        i + words.size <= tokens.size && words.indices.all { k -> tokens[i + k].isPlainWord() && tokens[i + k].lower == words[k] }

    private fun protocols(tokens: MutableList<Token>) {
        val forms = listOf(
            listOf("colon", "slash", "slash"),
            listOf("colon", "forward", "slash", "forward", "slash"),
            listOf("colon", "double", "slash"),
        )
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.isPlainWord() && (t.lower == "https" || t.lower == "http")) {
                val form = forms.firstOrNull { wordsAt(tokens, i + 1, it) }
                if (form != null) {
                    repeat(form.size) { tokens.removeAt(i + 1) }
                    t.text = t.lower + "://"
                    t.glueAfter = true
                }
            }
            i++
        }
    }

    /** "docs dot google dot com" → docs.google.com, "www dot site dot in" → www.site.in */
    private fun joinDots(tokens: MutableList<Token>) {
        var i = 0
        while (i < tokens.size) {
            val x = tokens[i]
            if (!x.isPlainWord() || x.lower in NOT_A_NAME || x.lower == "dot") { i++; continue }
            // Find the chain x (dot y)+ and accept it only if it ends in an extension/TLD or starts with www.
            var j = i
            var lastPart: String? = null
            while (wordsAt(tokens, j + 1, listOf("dot")) && tokens.getOrNull(j + 2).isPlainWord()) {
                lastPart = tokens[j + 2].lower
                j += 2
            }
            if (lastPart != null && (lastPart in EXTENSIONS_AND_TLDS || x.lower == "www")) {
                val sb = StringBuilder(x.text)
                var k = i + 1
                while (k < j + 1) { sb.append('.').append(tokens[k + 1].lower); k += 2 }
                x.text = sb.toString()
                for (r in i + 1..j) tokens.removeAt(i + 1)
            }
            i++
        }
    }

    private fun joinInfix(tokens: MutableList<Token>, op: List<String>, symbol: String) {
        var i = 1
        while (i < tokens.size) {
            if (wordsAt(tokens, i, op) && tokens[i - 1].isPlainWord() && tokens[i - 1].lower !in NOT_A_NAME &&
                tokens.getOrNull(i + op.size).isPlainWord()) {
                val right = tokens[i + op.size]
                tokens[i - 1].text = tokens[i - 1].text + symbol + right.text
                repeat(op.size + 1) { tokens.removeAt(i) }
                continue
            }
            i++
        }
    }

    private fun looksLikeDomain(s: String): Boolean {
        val dot = s.lastIndexOf('.')
        return dot > 0 && s.substring(dot + 1).lowercase() in TLDS
    }

    private fun emails(tokens: MutableList<Token>) {
        val ops = listOf(
            listOf("at", "the", "rate", "of"), listOf("at", "the", "rate"),
            listOf("at", "sign"), listOf("at", "symbol"), listOf("at"),
        )
        var i = 1
        while (i < tokens.size) {
            val op = ops.firstOrNull { wordsAt(tokens, i, it) }
            val user = tokens[i - 1]
            val domain = op?.let { tokens.getOrNull(i + it.size) }
            if (op != null && domain.isPlainWord() && user.isPlainWord() && looksLikeDomain(domain!!.text) &&
                user.lower !in NOT_A_NAME) {
                val plainAt = op.size == 1
                val cue = (maxOf(0, i - 6) until i).any { tokens[it].isWord && tokens[it].lower in EMAIL_CUES }
                if (!plainAt || domain.lower in EMAIL_DOMAINS || cue) {
                    user.text = user.text + "@" + domain.lower
                    repeat(op.size + 1) { tokens.removeAt(i) }
                    continue
                }
            }
            // "at sign john" → "@john"
            if (op != null && op.size == 2 && op[1] != "rate" && tokens.getOrNull(i + 2).isPlainWord()) {
                tokens[i + 2].text = "@" + tokens[i + 2].text
                repeat(2) { tokens.removeAt(i) }
                continue
            }
            i++
        }
    }
}
