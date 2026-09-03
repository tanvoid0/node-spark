package com.nodespark.lint

import org.junit.Assert.*
import org.junit.Test

class EslintOutputParserTest {

    // ── parse ───────────────────────────────────────────────────────────────

    private val realistic = """
    [
      {
        "filePath": "C:\\proj\\src\\app.js",
        "messages": [
          {
            "ruleId": "no-unused-vars",
            "severity": 1,
            "message": "'x' is assigned a value but never used.",
            "line": 2,
            "column": 7,
            "endLine": 2,
            "endColumn": 8
          },
          {
            "ruleId": "no-undef",
            "severity": 2,
            "message": "'foo' is not defined.",
            "line": 3,
            "column": 1,
            "endLine": 3,
            "endColumn": 4
          }
        ],
        "errorCount": 1,
        "warningCount": 1
      }
    ]
    """.trimIndent()

    @Test fun `parses both messages`() {
        val msgs = EslintOutputParser.parse(realistic)
        assertEquals(2, msgs.size)
        assertEquals("no-unused-vars", msgs[0].ruleId)
        assertEquals(1, msgs[0].severity)
        assertEquals(2, msgs[0].line)
        assertEquals(7, msgs[0].column)
        assertEquals(8, msgs[0].endColumn)
        assertEquals(2, msgs[1].severity)
        assertEquals("'foo' is not defined.", msgs[1].message)
    }

    @Test fun `fatal message has null ruleId and no end position`() {
        val json = """
        [{"filePath":"a.js","messages":[
          {"ruleId":null,"fatal":true,"severity":2,"message":"Parsing error: Unexpected token","line":1,"column":5}
        ]}]
        """.trimIndent()
        val m = EslintOutputParser.parse(json).single()
        assertNull(m.ruleId)
        assertNull(m.endLine)
        assertNull(m.endColumn)
        assertEquals(2, m.severity)
    }

    @Test fun `clean file yields nothing`() {
        assertTrue(EslintOutputParser.parse("""[{"filePath":"a.js","messages":[]}]""").isEmpty())
    }

    @Test fun `garbage yields nothing`() {
        assertTrue(EslintOutputParser.parse("").isEmpty())
        assertTrue(EslintOutputParser.parse("not json at all").isEmpty())
        assertTrue(EslintOutputParser.parse("""{"oops":"an object, not an array"}""").isEmpty())
        assertTrue(EslintOutputParser.parse("""[{"messages":"wrong type"}]""").isEmpty())
    }

    // ── toOffsets ───────────────────────────────────────────────────────────

    // "abc\ndefgh\nij"  -> lines start at 0, 4, 10; length 12
    private val starts = intArrayOf(0, 4, 10)
    private val len = 12

    private fun msg(line: Int, col: Int, endLine: Int? = null, endCol: Int? = null) =
        EslintMessage(null, 1, "m", line, col, endLine, endCol)

    @Test fun `single line range`() {
        assertEquals(5..7, EslintOutputParser.toOffsets(msg(2, 2, 2, 4), starts, len))
    }

    @Test fun `multi line range`() {
        assertEquals(1..11, EslintOutputParser.toOffsets(msg(1, 2, 3, 2), starts, len))
    }

    @Test fun `missing end gives a one char range`() {
        assertEquals(4..5, EslintOutputParser.toOffsets(msg(2, 1), starts, len))
    }

    @Test fun `zero width end is widened`() {
        assertEquals(4..5, EslintOutputParser.toOffsets(msg(2, 1, 2, 1), starts, len))
    }

    @Test fun `line past the end of the document is dropped`() {
        assertNull(EslintOutputParser.toOffsets(msg(9, 1), starts, len))
    }

    @Test fun `start past the text length is dropped`() {
        assertNull(EslintOutputParser.toOffsets(msg(3, 50), starts, len))
    }

    @Test fun `end is clamped to the text length`() {
        val r = EslintOutputParser.toOffsets(msg(3, 1, 3, 99), starts, len)!!
        assertEquals(10, r.first)
        assertEquals(len, r.last)
    }

    @Test fun `unusable end position falls back to a one char range`() {
        assertEquals(4..5, EslintOutputParser.toOffsets(msg(2, 1, 42, 3), starts, len))
    }
}
