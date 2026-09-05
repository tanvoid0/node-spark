package com.nodespark.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeModulesTest {

    private fun tempRoot(): File = Files.createTempDirectory("nodespark-nm").toFile().apply { deleteOnExit() }

    private fun File.pkg() = apply { mkdirs(); File(this, "package.json").writeText("{}") }
    private fun File.modules() = apply { File(this, "node_modules").mkdirs() }
    private fun File.child(rel: String) = File(this, rel).apply { mkdirs() }

    @Test fun `package json without node_modules banners on the file`() {
        val root = tempRoot().pkg()
        val src = File(root.child("src"), "index.ts").apply { writeText("") }
        assertEquals(root.absolutePath, NodeModules.missingModulesRoot(src.absolutePath))
    }

    @Test fun `installed node_modules means no banner`() {
        val root = tempRoot().pkg().modules()
        assertNull(NodeModules.missingModulesRoot(File(root, "index.js").absolutePath))
    }

    @Test fun `directory path works as well as a file path`() {
        val root = tempRoot().pkg()
        assertEquals(root.absolutePath, NodeModules.missingModulesRoot(root.child("a/b").absolutePath))
    }

    @Test fun `hoisted monorepo deps at the repo root suppress the banner`() {
        val repo = tempRoot().pkg().modules()
        val pkgA = repo.child("packages/a").pkg()
        assertNull(NodeModules.missingModulesRoot(File(pkgA, "index.ts").absolutePath))
    }

    @Test fun `nested package without deps anywhere reports the nearest root`() {
        val repo = tempRoot().pkg()
        val pkgA = repo.child("packages/a").pkg()
        assertEquals(pkgA.absolutePath, NodeModules.missingModulesRoot(File(pkgA, "index.ts").absolutePath))
    }

    @Test fun `files inside node_modules never banner`() {
        val root = tempRoot().pkg()
        val dep = root.child("node_modules/left-pad").pkg()
        assertNull(NodeModules.missingModulesRoot(File(dep, "index.js").absolutePath))
    }

    @Test fun `no package json anywhere means no banner`() {
        val plain = tempRoot()
        assertNull(NodeModules.missingModulesRoot(File(plain, "script.js").absolutePath))
    }

    @Test fun `the owning package is the nearest one, installed or not`() {
        val repo = tempRoot().pkg().modules()
        val pkgA = repo.child("packages/a").pkg()
        val nested = repo.child("node_modules/left-pad").pkg()
        assertEquals(pkgA.absolutePath, NodeModules.packageRoot(File(pkgA, "index.ts").absolutePath))
        assertEquals(repo.absolutePath, NodeModules.packageRoot(File(repo, "index.ts").absolutePath))
        assertNull(NodeModules.packageRoot(File(nested, "index.js").absolutePath))
    }
}
