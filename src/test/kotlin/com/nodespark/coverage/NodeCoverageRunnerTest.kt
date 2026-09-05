package com.nodespark.coverage

import com.intellij.rt.coverage.data.ProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The lcov -> ProjectData conversion the Coverage tool window and the editor gutters both read.
 * No fixture needed: neither the runner nor ProjectData touches the IDE.
 */
class NodeCoverageRunnerTest {

    private val runner = NodeCoverageRunner()
    private val dir = Files.createTempDirectory("nodespark-lcov").toFile()

    /** With no suite to name a project, the runner resolves relative SF: paths against this dir. */
    private fun load(text: String): ProjectData? =
        runner.loadCoverageData(File(dir, "lcov.info").apply { writeText(text) }, null)

    @Test fun `hits and misses survive the round trip`() {
        val data = load(
            """
            SF:src/a.js
            DA:1,7
            DA:3,0
            end_of_record
            """.trimIndent(),
        )!!

        // Keyed the way the engine names an open file, and nothing else: get this wrong and the
        // percentages are right while every gutter is blank.
        val classData = data.getClassData(LcovParser.key("src/a.js", dir))
        assertEquals(7, classData.getLineData(1).hits)
        assertEquals(0, classData.getLineData(3).hits)
        assertNull("line 2 was never reported", classData.getLineData(2))
    }

    /**
     * Jest on Windows writes an SF: path with backslashes; the editor asks with forward slashes.
     * Both go through [LcovParser.key], which is the only reason they ever meet.
     */
    @Test fun `a native separator SF path keys the same as the editor's own path`() {
        val asJestWrites = File("src", "math.js").path          // src\math.js on Windows
        val data = load("SF:$asJestWrites\nDA:1,1\nend_of_record\n")!!
        val asTheEditorAsks = LcovParser.key(
            File(dir, "src/math.js").path.replace(File.separatorChar, '/'), dir,
        )
        assertEquals(1, data.getClassData(asTheEditorAsks).getLineData(1).hits)
    }

    @Test fun `a report with no line data loads nothing`() {
        assertNull(load("TN:\n"))
        assertNull(runner.loadCoverageData(File(dir, "absent.info"), null))
    }
}

/** Which files the tool window counts as uncovered rather than ignoring outright. */
class UncoveredSourceFilterTest {

    private fun countable(path: String) = isCountableSource(File(path))

    @Test fun `node sources outside generated directories count`() {
        assertTrue(countable("/proj/src/a.ts"))
        assertTrue(countable("/proj/test/a.spec.js"))
        assertFalse("not a node source", countable("/proj/src/style.css"))
        assertFalse("no executable lines", countable("/proj/src/types.d.ts"))
        assertFalse("generated", countable("/proj/src/vendor.min.js"))
    }

    @Test fun `anything under a skipped directory is ignored at any depth`() {
        assertFalse(countable("/proj/node_modules/left-pad/index.js"))
        assertFalse(countable("/proj/packages/app/node_modules/x/deep/y.js"))
        assertFalse(countable("/proj/dist/bundle.js"))
        assertTrue(countable("/proj/src/distance.ts"))
    }
}
