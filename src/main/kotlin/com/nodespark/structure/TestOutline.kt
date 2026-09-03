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
            (if (stack.isEmpty()) roots else stack[stack.size - 1].children).add(node)
            stack.add(node)
        }
        return roots.map { it.build() }
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

    private class Scan(val depth: IntArray, val code: BooleanArray)

    /**
     * One left-to-right pass recording, per offset, the enclosing brace depth and whether that
     * offset is real code (not a line/block comment and not inside a quoted or template string).
     * `${` inside a template re-enters code mode; the matching `}` returns to the template.
     */
    private fun scan(t: CharSequence): Scan {
        val n = t.length
        val depth = IntArray(n)
        val code = BooleanArray(n)
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
        return Scan(depth, code)
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
    fun build(): OutlineNode = OutlineNode(kind, name, offset, modifier, children.map { it.build() })
}
