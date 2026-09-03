package com.nodespark.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeCommandLineTest {

    // ── parseEnvVars ────────────────────────────────────────────────────────

    @Test fun `blank is empty`() {
        assertTrue(NodeCommandLine.parseEnvVars("").isEmpty())
        assertTrue(NodeCommandLine.parseEnvVars("   ").isEmpty())
    }

    @Test fun `single pair`() {
        assertEquals(mapOf("NODE_ENV" to "test"), NodeCommandLine.parseEnvVars("NODE_ENV=test"))
    }

    @Test fun `multiple pairs are trimmed`() {
        assertEquals(
            mapOf("A" to "1", "B" to "2"),
            NodeCommandLine.parseEnvVars(" A = 1 , B = 2 "),
        )
    }

    @Test fun `value keeps its own equals signs`() {
        assertEquals(mapOf("URL" to "a=b=c"), NodeCommandLine.parseEnvVars("URL=a=b=c"))
    }

    @Test fun `entries without equals are dropped`() {
        assertEquals(mapOf("A" to "1"), NodeCommandLine.parseEnvVars("A=1,garbage"))
    }

    // ── cmd shim rule ───────────────────────────────────────────────────────

    @Test fun `cmd shims are detected case insensitively`() {
        assertTrue(NodeCommandLine.isCmdShim("C:\\p\\node_modules\\.bin\\jest.CMD"))
        assertFalse(NodeCommandLine.isCmdShim("/p/node_modules/.bin/jest"))
    }

    // ── onPath: a bare name must never reach exePath ────────────────────

    @Test fun `an absolute path is returned untouched`() {
        val abs = File("node_modules/.bin/eslint").absolutePath
        assertEquals(abs, NodeCommandLine.onPath(abs))
    }

    @Test fun `a name found on PATH becomes absolute`() {
        // java(.exe) is on PATH wherever this test can run at all.
        val resolved = NodeCommandLine.onPath("java")
        assertTrue("expected an absolute path, got: $resolved", File(resolved).isAbsolute)
        assertTrue(File(resolved).isFile)
    }

    @Test fun `a name that is not on PATH is left alone rather than resolved against the cwd`() {
        assertEquals("nodespark-no-such-binary", NodeCommandLine.onPath("nodespark-no-such-binary"))
    }

    // ── NodeProjectUtil.findNearestPackageJson (no Project needed) ───────────

    @Test fun `nearest package json walks up from a file`() {
        val root = Files.createTempDirectory("nodespark-root").toFile()
        root.deleteOnExit()
        File(root, "package.json").writeText("{}")
        val nested = File(root, "src/deep").apply { mkdirs() }
        val file = File(nested, "a.test.js").apply { writeText("") }

        assertEquals(
            File(root, "package.json").canonicalPath,
            NodeProjectUtil.findNearestPackageJson(file.absolutePath)!!.canonicalPath,
        )
    }

    @Test fun `nearest package json stops at the boundary`() {
        val root = Files.createTempDirectory("nodespark-stop").toFile()
        root.deleteOnExit()
        val nested = File(root, "a/b").apply { mkdirs() }
        assertNull(NodeProjectUtil.findNearestPackageJson(nested.absolutePath, root.absolutePath))
    }
}
