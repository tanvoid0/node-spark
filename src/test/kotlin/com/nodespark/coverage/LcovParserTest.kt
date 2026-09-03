package com.nodespark.coverage

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LcovParserTest {

    private val base = File("C:/proj").absoluteFile

    private fun keyOf(path: String) = LcovParser.key(path, base)

    @Test fun `multi-file report with LF LH BRDA FN noise`() {
        val text = """
            TN:
            SF:src/a.js
            FN:1,alpha
            FNDA:2,alpha
            DA:1,3
            DA:2,0
            BRDA:2,0,0,1
            LF:2
            LH:1
            end_of_record
            SF:src/b.js
            DA:10,1
            DA:11,1
            LF:2
            LH:2
            end_of_record
        """.trimIndent()

        val data = LcovParser.parse(text, base)
        assertEquals(2, data.size)
        assertEquals(mapOf(1 to 3, 2 to 0), data[keyOf("src/a.js")])
        assertEquals(mapOf(10 to 1, 11 to 1), data[keyOf("src/b.js")])
    }

    @Test fun `missing trailing end_of_record still flushes`() {
        val data = LcovParser.parse("SF:src/a.js\nDA:1,1\n", base)
        assertEquals(mapOf(1 to 1), data[keyOf("src/a.js")])
    }

    @Test fun `CRLF line endings`() {
        val data = LcovParser.parse("SF:src/a.js\r\nDA:1,1\r\nDA:2,0\r\nend_of_record\r\n", base)
        assertEquals(mapOf(1 to 1, 2 to 0), data[keyOf("src/a.js")])
    }

    @Test fun `duplicate DA lines for the same line sum`() {
        val data = LcovParser.parse("SF:src/a.js\nDA:1,2\nDA:1,3\nend_of_record\n", base)
        assertEquals(mapOf(1 to 5), data[keyOf("src/a.js")])
    }

    @Test fun `malformed DA lines are skipped not fatal`() {
        val data = LcovParser.parse("SF:src/a.js\nDA:x,y\nDA:2\nDA:3,4,checksum\nend_of_record\n", base)
        assertEquals(mapOf(3 to 4), data[keyOf("src/a.js")])
    }

    @Test fun `absolute windows SF matches a virtual file path`() {
        assumeTrue(File.separatorChar == '\\')
        val data = LcovParser.parse("SF:C:\\proj\\src\\a.js\nDA:1,1\nend_of_record\n", base)
        // the VirtualFile side uses forward slashes — both sides must land on the same key
        assertEquals(mapOf(1 to 1), data[keyOf("C:/proj/src/a.js")])
        assertEquals("c:/proj/src/a.js", keyOf("C:\\Proj\\src\\a.js"))
    }

    @Test fun `relative SF resolves against baseDir`() {
        val data = LcovParser.parse("SF:src/a.js\nDA:1,1\nend_of_record\n", base)
        assertEquals(mapOf(1 to 1), data[keyOf(File(base, "src/a.js").path)])
    }

    @Test fun `empty input is empty`() {
        assertTrue(LcovParser.parse("", base).isEmpty())
    }
}
