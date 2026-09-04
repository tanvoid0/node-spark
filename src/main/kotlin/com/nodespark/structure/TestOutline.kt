package com.nodespark.structure

import java.util.regex.Pattern

enum class TestKind { DESCRIBE, TEST }

enum class TestModifier { NONE, ONLY, SKIP, TODO }

data class OutlineNode(
    val kind: TestKind,
    val name: String,
    val offset: Int,
    val modifier: TestModifier,
    val children: List<OutlineNode>,
    /** Offset just past this block's closing brace; end of file when the callback has no body. */
    val endOffset: Int = offset,
)

/**
 * Pure describe/it tree extraction. No IntelliJ types — this is the whole value of the feature and
 * the only part worth unit testing.
 */
object TestOutline {

    // COPY of NodeTestDetector.TEST_FUNCTION_PATTERN (that file belongs to another feature, so it is
    // duplicated here rather than edited), widened for:
    //   - chained modifiers: it.skip.each
    //   - the two-call each forms: test.each([...])('%s', ...) and test.each`table`('...', ...)
    //   - context / suite / specify
    //   - no ^ line anchor, so `describe('a', () => { it('b', () => {}) })` yields both nodes
    // Groups: 1 = keyword, 2 = modifier chain, 3 = quote, 4 = name.
    private val PATTERN: Pattern = Pattern.compile(
        """(?<![\w.])(describe|context|suite|it|test|specify)""" +
            """((?:\.(?:only|skip|todo|concurrent|sequential|failing|each))*)""" +
            """(?:\s*(?:\((?:[^()]|\([^()]*\))*\)|`[^`]*`))?""" +
            """\s*\(\s*(['"`])((?:\\.|(?!\3)[^\r\n])*)\3"""
    )

    private val ESCAPE = Regex("""\\(.)""")

    // ponytail: regex + a brace-depth scanner, not a JS parser. Ceiling: regex literals with
    // unbalanced braces (/}{/), JSX braces in .jsx/.tsx, and names built by concatenation or held in
    // a variable. IC 2024.1 ships no JS PSI, so there is no upgrade path short of vendoring a JS
    // parser — accept the ceiling.
    fun parse(text: CharSequence): List<OutlineNode> {
        if (text.isEmpty()) return emptyList()
        val scan = scan(text)
        val roots = ArrayList<Builder>()
        val stack = ArrayList<Builder>()
        val m = PATTERN.matcher(text)
        while (m.find()) {
            val at = m.start(1)
            if (!scan.code[at]) continue // inside a comment or a string literal
            val keyword = m.group(1)
            val node = Builder(
                kind = if (keyword == "describe" || keyword == "context" || keyword == "suite")
                    TestKind.DESCRIBE else TestKind.TEST,
                name = ESCAPE.replace(m.group(4) ?: "") { it.groupValues[1] },
                offset = at,
                modifier = modifierOf(m.group(2) ?: ""),
                depth = scan.depth[at],
            )
            // Nest under the last still-open node: a describe at brace depth d opens its callback
            // body at d+1, so anything deeper belongs to it and anything at d or less closes it.
            while (stack.isNotEmpty() && stack[stack.size - 1].depth >= node.depth) {
                stack.removeAt(stack.size - 1)
            }
            node.end = endOf(scan, m.start(3))
            (if (stack.isEmpty()) roots else stack[stack.size - 1].children).add(node)
            stack.add(node)
        }
        return roots.map { it.build() }
    }

    /**
     * The describe/it chain enclosing [offset], outermost first: ["UserService", "login",
     * "returns a token"]. Empty when the offset is not inside any test block.
     */
    fun pathAt(text: CharSequence, offset: Int): List<OutlineNode> = pathIn(parse(text), offset)

    fun pathIn(nodes: List<OutlineNode>, offset: Int): List<OutlineNode> {
        val node = nodes.lastOrNull { offset in it.offset until it.endOffset.coerceAtLeast(it.offset + 1) }
            ?: return emptyList()
        return listOf(node) + pathIn(node.children, offset)
    }

    /**
     * End of the whole `describe(...)` call: the offset just past the `)` that closes the call
     * whose argument list opens immediately before the name string at [nameQuote].
     *
     * Brace depth is the wrong thing to follow here. `it('a', () => expect(1).toBe(1))` has no body
     * braces at all, and `test('a', { concurrent: true }, fn)` opens its first brace on an options
     * object rather than on the callback — both mis-measure a block, and pathAt then attributes the
     * wrong test to the caret. The call's own parentheses bound it exactly, in every form.
     */
    private fun endOf(scan: Scan, nameQuote: Int): Int {
        var open = nameQuote - 1
        while (open >= 0 && scan.text[open].isWhitespace()) open--
        if (open < 0 || scan.text[open] != '(') return nameQuote
        return scan.closeOf[open].takeIf { it >= 0 } ?: scan.depth.size
    }

    private fun modifierOf(chain: String): TestModifier {
        for (part in chain.split('.')) {
            when (part) {
                "only" -> return TestModifier.ONLY
                "skip" -> return TestModifier.SKIP
                "todo" -> return TestModifier.TODO
            }
        }
        return TestModifier.NONE
    }

    /**
     * @param depth brace depth at each offset
     * @param code true where the offset is real code, not a comment or a string literal
     * @param closeOf for each `(` offset, the offset just past its matching `)`; -1 elsewhere
     */
    private class Scan(
        val text: CharSequence,
        val depth: IntArray,
        val code: BooleanArray,
        val closeOf: IntArray,
    )

    /**
     * One left-to-right pass recording, per offset, the enclosing brace depth and whether that
     * offset is real code (not a line/block comment and not inside a quoted or template string).
     * `${` inside a template re-enters code mode; the matching `}` returns to the template.
     */
    private fun scan(t: CharSequence): Scan {
        val n = t.length
        val depth = IntArray(n)
        val code = BooleanArray(n)
        val closeOf = IntArray(n) { -1 }
        val openParens = ArrayList<Int>()     // offsets of unclosed `(`
        val templateDepths = ArrayList<Int>() // brace depth at each open `${`
        var d = 0
        var mode = NORMAL
        var i = 0
        while (i < n) {
            val c = t[i]
            depth[i] = d
            code[i] = mode == NORMAL
            when (mode) {
                NORMAL -> when {
                    c == '/' && i + 1 < n && t[i + 1] == '/' -> { mode = LINE_COMMENT; i++ }
                    c == '/' && i + 1 < n && t[i + 1] == '*' -> { mode = BLOCK_COMMENT; i++ }
                    c == '\'' -> mode = SINGLE
                    c == '"' -> mode = DOUBLE
                    c == '`' -> mode = TEMPLATE
                    c == '(' -> openParens.add(i)
                    c == ')' -> if (openParens.isNotEmpty()) {
                        closeOf[openParens.removeAt(openParens.size - 1)] = i + 1
                    }
                    c == '{' -> d++
                    c == '}' ->
                        if (templateDepths.isNotEmpty() && templateDepths[templateDepths.size - 1] == d) {
                            templateDepths.removeAt(templateDepths.size - 1)
                            mode = TEMPLATE
                        } else if (d > 0) {
                            d--
                        }
                }
                LINE_COMMENT -> if (c == '\n') mode = NORMAL
                BLOCK_COMMENT -> if (c == '*' && i + 1 < n && t[i + 1] == '/') { mode = NORMAL; i++ }
                SINGLE, DOUBLE -> when {
                    c == '\\' -> i++
                    c == '\n' -> mode = NORMAL // unterminated literal: recover at end of line
                    c == '\'' && mode == SINGLE -> mode = NORMAL
                    c == '"' && mode == DOUBLE -> mode = NORMAL
                }
                TEMPLATE -> when {
                    c == '\\' -> i++
                    c == '`' -> mode = NORMAL
                    c == '$' && i + 1 < n && t[i + 1] == '{' -> {
                        templateDepths.add(d)
                        mode = NORMAL
                        i++
                    }
                }
            }
            i++
        }
        return Scan(t, depth, code, closeOf)
    }

    private const val NORMAL = 0
    private const val LINE_COMMENT = 1
    private const val BLOCK_COMMENT = 2
    private const val SINGLE = 3
    private const val DOUBLE = 4
    private const val TEMPLATE = 5
}

private class Builder(
    val kind: TestKind,
    val name: String,
    val offset: Int,
    val modifier: TestModifier,
    val depth: Int,
    val children: MutableList<Builder> = ArrayList(),
) {
    var end: Int = offset

    fun build(): OutlineNode =
        OutlineNode(kind, name, offset, modifier, children.map { it.build() }, end)
}
