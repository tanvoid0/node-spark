package com.nodespark.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodePackageManagerTest {

    private fun dirWith(vararg files: Pair<String, String>): String {
        val dir = Files.createTempDirectory("nodespark-pm").toFile()
        dir.deleteOnExit()
        files.forEach { (name, text) -> File(dir, name).writeText(text) }
        return dir.absolutePath
    }

    // ── lockfile detection ──────────────────────────────────────────────────

    @Test fun `pnpm lockfile wins`() {
        assertEquals(NodePackageManager.PNPM, NodePackageManager.detect(dirWith("pnpm-lock.yaml" to "")))
    }

    @Test fun `yarn lockfile detected`() {
        assertEquals(NodePackageManager.YARN, NodePackageManager.detect(dirWith("yarn.lock" to "")))
    }

    @Test fun `bun lockb detected`() {
        assertEquals(NodePackageManager.BUN, NodePackageManager.detect(dirWith("bun.lockb" to "")))
    }

    @Test fun `bun lock text file detected`() {
        assertEquals(NodePackageManager.BUN, NodePackageManager.detect(dirWith("bun.lock" to "")))
    }

    @Test fun `npm lockfile detected`() {
        assertEquals(NodePackageManager.NPM, NodePackageManager.detect(dirWith("package-lock.json" to "")))
    }

    @Test fun `no lockfile defaults to npm`() {
        assertEquals(NodePackageManager.NPM, NodePackageManager.detect(dirWith()))
    }

    // ── packageManager field beats lockfiles ────────────────────────────────

    @Test fun `packageManager field overrides lockfile`() {
        val dir = dirWith(
            "package-lock.json" to "",
            "package.json" to """{"name":"x","packageManager":"pnpm@9.1.0"}""",
        )
        assertEquals(NodePackageManager.PNPM, NodePackageManager.detect(dir))
    }

    @Test fun `package json without the field falls back to lockfile`() {
        val dir = dirWith("yarn.lock" to "", "package.json" to """{"name":"x"}""")
        assertEquals(NodePackageManager.YARN, NodePackageManager.detect(dir))
    }

    @Test fun `unknown packageManager value falls back to lockfile`() {
        val dir = dirWith("yarn.lock" to "", "package.json" to """{"packageManager":"deno@1.0.0"}""")
        assertEquals(NodePackageManager.YARN, NodePackageManager.detect(dir))
    }

    @Test fun `fromSpec parses name before the version`() {
        assertEquals(NodePackageManager.BUN, NodePackageManager.fromSpec("""{"packageManager": "bun@1.1.0"}"""))
        assertNull(NodePackageManager.fromSpec("""{"name":"x"}"""))
    }

    // ── argv builders ───────────────────────────────────────────────────────

    @Test fun `run argv omits run for yarn only`() {
        assertEquals(listOf("run", "build"), NodePackageManager.NPM.runArgs("build"))
        assertEquals(listOf("build"), NodePackageManager.YARN.runArgs("build"))
        assertEquals(listOf("run", "build"), NodePackageManager.PNPM.runArgs("build"))
        assertEquals(listOf("run", "build"), NodePackageManager.BUN.runArgs("build"))
    }

    @Test fun `windows binaries use cmd shims except bun`() {
        assertEquals("npm.cmd", NodePackageManager.NPM.binary(windows = true))
        assertEquals("pnpm.cmd", NodePackageManager.PNPM.binary(windows = true))
        assertEquals("bun", NodePackageManager.BUN.binary(windows = true))
        assertEquals("npm", NodePackageManager.NPM.binary(windows = false))
    }

    @Test fun `install argv`() {
        assertEquals(listOf("install"), NodePackageManager.YARN.installArgs())
    }
}
