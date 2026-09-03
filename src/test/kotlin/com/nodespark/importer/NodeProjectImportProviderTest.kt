package com.nodespark.importer

import com.nodespark.tempDir
import com.intellij.projectImport.ProjectImportProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class NodeProjectImportProviderTest : BasePlatformTestCase() {

    private val provider = NodeProjectImportProvider()

    // ── registration ────────────────────────────────────────────────────────

    fun `test_provider is registered on the import extension point`() {
        val registered = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.extensionList
        assertTrue(
            "NodeProjectImportProvider missing — check node-spark-java.xml and the optional java dependency",
            registered.any { it is NodeProjectImportProvider },
        )
    }

    fun `test_builder is a node builder`() {
        assertTrue(provider.builder is NodeProjectImportBuilder)
    }

    // ── canImport ───────────────────────────────────────────────────────────

    fun `test_package json is importable`() {
        val pkg = myFixture.addFileToProject("app/package.json", "{}").virtualFile
        assertTrue(provider.canImport(pkg, project))
    }

    fun `test_other files are not importable`() {
        val other = myFixture.addFileToProject("app/index.js", "").virtualFile
        assertFalse(provider.canImport(other, project))
    }

    fun `test_directory with package json is importable`() {
        val dir = myFixture.addFileToProject("withpkg/package.json", "{}").virtualFile.parent
        assertTrue(provider.canImport(dir, project))
    }

    fun `test_directory without package json is not importable`() {
        val dir = myFixture.addFileToProject("nopkg/index.js", "").virtualFile.parent
        assertFalse(provider.canImport(dir, project))
    }

    // ── contentRootFor ──────────────────────────────────────────────────────

    fun `test_content root of a package json is its directory`() {
        val dir = tempDir()
        val pkg = File(dir, "package.json").apply { writeText("{}") }
        assertEquals(dir.canonicalFile, NodeProjectImportBuilder.contentRootFor(pkg.path)?.canonicalFile)
        dir.deleteRecursively()
    }

    fun `test_content root of a directory is the directory itself`() {
        val dir = tempDir()
        assertEquals(dir.canonicalFile, NodeProjectImportBuilder.contentRootFor(dir.path)?.canonicalFile)
        dir.deleteRecursively()
    }

    fun `test_blank and null paths yield no content root`() {
        assertNull(NodeProjectImportBuilder.contentRootFor(null))
        assertNull(NodeProjectImportBuilder.contentRootFor(""))
        assertNull(NodeProjectImportBuilder.contentRootFor("   "))
    }
}
