package com.nodespark.lsp

import com.nodespark.tempDir
import com.nodespark.util.NodeRunnerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The language server is only ever launched as `node <entry>`, so the whole feature hinges on
 * finding that entry inside the project's own node_modules.
 */
class TsServerEntryTest {

    private val pkg = "typescript-language-server"

    private fun resolve(root: File) = NodeRunnerEntry.resolveEntry(listOf(pkg), pkg, root, root)

    private fun install(root: File, binField: String): File {
        val dir = File(root, "node_modules/$pkg")
        dir.mkdirs()
        File(dir, "package.json").writeText("""{"name":"$pkg","version":"4.3.3","bin":$binField}""")
        val cli = File(dir, "lib/cli.mjs")
        cli.parentFile.mkdirs()
        cli.writeText("#!/usr/bin/env node\n")
        return cli
    }

    @Test fun `resolves the cli from an object bin field`() {
        val root = tempDir()
        val cli = install(root, """{"$pkg":"./lib/cli.mjs"}""")
        assertEquals(cli.canonicalPath, resolve(root))
    }

    @Test fun `resolves the cli from a string bin field`() {
        val root = tempDir()
        val cli = install(root, """"./lib/cli.mjs"""")
        assertEquals(cli.canonicalPath, resolve(root))
    }

    @Test fun `finds it in a parent workspace node_modules`() {
        val workspace = tempDir()
        val cli = install(workspace, """{"$pkg":"./lib/cli.mjs"}""")
        val packageDir = File(workspace, "packages/api").also { it.mkdirs() }
        assertEquals(cli.canonicalPath, resolve(packageDir))
    }

    /** Not installed means the feature stays off — never a PATH or global fallback. */
    @Test fun `null when the project does not have the server`() {
        assertNull(resolve(tempDir()))
    }

    /** A bin entry pointing at a file that was never unpacked must not be launched. */
    @Test fun `null when the declared entry is missing`() {
        val root = tempDir()
        val dir = File(root, "node_modules/$pkg").also { it.mkdirs() }
        File(dir, "package.json").writeText("""{"name":"$pkg","bin":{"$pkg":"./lib/cli.mjs"}}""")
        assertNull(resolve(root))
    }
}
