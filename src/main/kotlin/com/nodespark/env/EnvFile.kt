package com.nodespark.env

/**
 * Pure .env parsing and rendering. The file is modelled as a list of lines rather than a map, so
 * comments, blank lines and ordering survive a round trip through the table editor: only the
 * key/value lines the user actually touched are re-rendered.
 */
object EnvFile {

    private val KEY = Regex("[A-Za-z_][A-Za-z0-9_.-]*")

    sealed interface Line {
        /** Exact source text. A multi-line quoted value keeps its own line separators. */
        val text: String
    }

    /** A comment, a blank line, or anything that is not a `KEY=value` assignment. */
    data class Raw(override val text: String) : Line

    data class Pair(
        val key: String,
        val value: String,
        val exported: Boolean = false,
        /** Trailing `# ...` note on the same line, without the hash. Empty when there is none. */
        val comment: String = "",
        /**
         * The `#` block written directly above the assignment, hashes stripped, one entry per line.
         * This is what a .env file uses in place of a real multi-line comment, so it belongs to the
         * key rather than floating on its own.
         */
        val doc: String = "",
        override val text: String = render(key, value, exported, comment, doc),
        /** Offset of [key] inside [text]. */
        val keyOffset: Int = 0,
    ) : Line

    data class Parsed(val lines: List<Line>, val separator: String) {
        fun render(): String = lines.joinToString(separator) { it.text }

        /** Key/value lines only, in file order. */
        val pairs: List<Pair> get() = lines.filterIsInstance<Pair>()

        /** Absolute offset of each line's start in the rendered text. */
        fun offsets(): List<Int> {
            var at = 0
            return lines.map { val start = at; at += it.text.length + separator.length; start }
        }
    }

    fun parse(text: String): Parsed {
        val separator = if (text.contains("\r\n")) "\r\n" else "\n"
        val raw = text.split(separator)
        val lines = ArrayList<Line>(raw.size)
        var i = 0
        while (i < raw.size) {
            val (line, consumed) = parseAt(raw, i, separator)
            lines += if (line is Pair) attachDocBlock(lines, line, separator) else line
            i += consumed
        }
        return Parsed(lines, separator)
    }

    /** Convenience: last assignment wins, the way a dotenv loader sees the file. */
    fun toMap(text: String): Map<String, String> = parse(text).pairs.associate { it.key to it.value }

    /** Rewrites [text] so its assignments become exactly [pairs], keeping every other line in place. */
    fun withPairs(text: String, pairs: List<Pair>): String {
        val parsed = parse(text)
        val out = ArrayList<Line>()
        val remaining = ArrayDeque(pairs)
        for (line in parsed.lines) {
            if (line !is Pair) {
                out += line
                continue
            }
            // Assignments are replaced positionally: extras are appended, deletions drop the line.
            remaining.removeFirstOrNull()?.let { out += it }
        }
        out += remaining
        // A re-rendered multi-line pair carries plain "\n" inside its own text; on a CRLF file that
        // has to come back out as CRLF like every other line.
        return out.joinToString(parsed.separator) {
            if (parsed.separator == "\n") it.text else it.text.replace("\r\n", "\n").replace("\n", parsed.separator)
        }
    }

    fun render(
        key: String,
        value: String,
        exported: Boolean = false,
        comment: String = "",
        doc: String = "",
    ): String {
        val assignment = (if (exported) "export " else "") + key + "=" + quote(value) +
            if (comment.isBlank()) "" else "  # " + comment.trim()
        if (doc.isBlank()) return assignment
        return doc.trim().split("\n").joinToString("\n") { ("# " + it.trim()).trimEnd() } + "\n" + assignment
    }

    /**
     * Moves the run of comment lines immediately above [pair] out of [lines] and onto the pair, so
     * that a key and the lines documenting it travel together. A block separated from the key by a
     * blank line stays where it is: that is a section header, not a description of one variable.
     */
    private fun attachDocBlock(lines: MutableList<Line>, pair: Pair, separator: String): Pair {
        var from = lines.size
        while (from > 0) {
            val above = lines[from - 1]
            if (above !is Raw || !above.text.trimStart().startsWith("#")) break
            from--
        }
        if (from == lines.size) return pair
        val block = lines.subList(from, lines.size).map { it.text }
        repeat(block.size) { lines.removeAt(lines.size - 1) }
        val prefix = block.joinToString(separator) + separator
        return pair.copy(
            doc = block.joinToString("\n") { it.trimStart().removePrefix("#").trim() },
            text = prefix + pair.text,
            keyOffset = prefix.length + pair.keyOffset,
        )
    }

    /** Quotes only when the value would not survive as-is. */
    fun quote(value: String): String {
        val plain = value.isNotEmpty() &&
            value.none { it.isWhitespace() || it == '#' || it == '"' || it == '\'' || it == '$' || it == '`' }
        if (plain) return value
        // `$` inside double quotes is expanded by shells and some loaders; single quotes are literal.
        if (value.contains('$') && !value.contains('\'') && !value.contains('\n')) return "'" + value + "'"
        val sb = StringBuilder("\"")
        for (c in value) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    /** Keys assigned more than once: key, start offset and end offset of every repeat. */
    fun duplicateKeyRanges(text: String): List<Triple<String, Int, Int>> {
        val parsed = parse(text)
        val offsets = parsed.offsets()
        val seen = HashSet<String>()
        val dups = ArrayList<Triple<String, Int, Int>>()
        parsed.lines.forEachIndexed { idx, line ->
            if (line is Pair && !seen.add(line.key)) {
                val start = offsets[idx] + line.keyOffset
                dups += Triple(line.key, start, start + line.key.length)
            }
        }
        return dups
    }

    /** Returns the line starting at [i] and how many source lines it consumed. */
    private fun parseAt(raw: List<String>, i: Int, separator: String): kotlin.Pair<Line, Int> {
        val line = raw[i]
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return Raw(line) to 1

        var rest = trimmed
        var exported = false
        if (rest.startsWith("export ") || rest.startsWith("export\t")) {
            exported = true
            rest = rest.removePrefix("export").trimStart()
        }
        val eq = rest.indexOf('=')
        if (eq <= 0) return Raw(line) to 1
        val key = rest.substring(0, eq).trim()
        if (!KEY.matches(key)) return Raw(line) to 1

        val keyOffset = line.length - rest.length + rest.indexOf(key)
        val after = rest.substring(eq + 1)
        val open = after.trimStart().firstOrNull()
        if (open == '"' || open == '\'' || open == '`') {
            val body = after.trimStart().substring(1)
            val closed = closeQuote(body, open)
            if (closed >= 0) {
                val value = unescape(body.substring(0, closed), open)
                val note = commentIn(body.substring(closed + 1))
                return Pair(key, value, exported, note, text = line, keyOffset = keyOffset) to 1
            }
            // Unterminated: a multi-line value, running to the closing quote or the end of the file.
            val sb = StringBuilder(body)
            var n = 1
            while (i + n < raw.size) {
                val next = raw[i + n]
                sb.append('\n')
                val end = closeQuote(next, open)
                n++
                if (end >= 0) {
                    sb.append(next, 0, end)
                    val src = raw.subList(i, i + n).joinToString(separator)
                    val note = commentIn(next.substring(end + 1))
                    return Pair(key, unescape(sb.toString(), open), exported, note, text = src, keyOffset = keyOffset) to n
                }
                sb.append(next)
            }
            val src = raw.subList(i, i + n).joinToString(separator)
            return Pair(key, unescape(sb.toString(), open), exported, "", text = src, keyOffset = keyOffset) to n
        }

        // Unquoted: an inline comment needs whitespace in front of the `#`, as in dotenv.
        var value = after
        var note = ""
        val hash = Regex("(^|\\s)#").find(value)
        if (hash != null) {
            note = commentIn(value.substring(hash.range.first))
            value = value.substring(0, hash.range.first)
        }
        return Pair(key, value.trim(), exported, note, text = line, keyOffset = keyOffset) to 1
    }

    /** The text after the first `#` in [s], trimmed, or "" when there is none. */
    private fun commentIn(s: String): String {
        val hash = s.indexOf('#')
        return if (hash < 0) "" else s.substring(hash + 1).trim()
    }

    /** Index of the unescaped closing [quote] in [s], or -1. */
    private fun closeQuote(s: String, quote: Char): Int {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && quote == '"') {
                i += 2
                continue
            }
            if (c == quote) return i
            i++
        }
        return -1
    }

    private fun unescape(s: String, quote: Char): String {
        if (quote != '"') return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                i++
                when (val e = s[i]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    else -> sb.append('\\').append(e)
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
