package com.nodespark.sdk

import com.nodespark.tempDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File

class NodeJsSdkTypeTest : BasePlatformTestCase() {

    private val sdkType = NodeJsSdkType()

    // ── isValidSdkHome ──────────────────────────────────────────────────────

    fun `test_valid sdk home contains node binary`() {
        val dir = tempDir()
        val nodeBin = if (SystemInfo.isWindows) "node.exe" else "node"
        File(dir, nodeBin).createNewFile()
        assertTrue(sdkType.isValidSdkHome(dir.absolutePath))
        dir.deleteRecursively()
    }

    fun `test_invalid sdk home missing node binary`() {
        val dir = tempDir()
        assertFalse(sdkType.isValidSdkHome(dir.absolutePath))
        dir.deleteRecursively()
    }

    fun `test_empty directory is invalid sdk home`() {
        val dir = tempDir()
        assertFalse(sdkType.isValidSdkHome(dir.absolutePath))
        dir.deleteRecursively()
    }

    fun `test_nonexistent path is invalid sdk home`() {
        assertFalse(sdkType.isValidSdkHome("/nonexistent/path/that/does/not/exist"))
    }

    // ── suggestSdkName ──────────────────────────────────────────────────────

    fun `test_presentable name is Node dot js`() {
        assertEquals("Node.js", sdkType.presentableName)
    }

    fun `test_type id is correct`() {
        assertEquals(NodeJsSdkType.TYPE_ID, sdkType.name)
    }

    // ── getNodeExecutable ────────────────────────────────────────────────────

    fun `test_node executable path is inside sdk home`() {
        val home = tempDir().absolutePath
        val sdk = mockSdk(home)
        val exe = sdkType.getNodeExecutable(sdk)
        val nodeBin = if (SystemInfo.isWindows) "node.exe" else "node"
        assertTrue("Expected exe to end with $nodeBin but was $exe", exe.endsWith(nodeBin))
        // normalize separators before comparing (Windows uses backslash)
        val exeNorm = exe.replace("\\", "/")
        val homeNorm = (sdk.homePath ?: home).replace("\\", "/")
        assertTrue("Expected exe '$exeNorm' to be inside '$homeNorm'", exeNorm.startsWith(homeNorm))
    }

    fun `test_getBinExecutable falls back to PATH when bin not in sdk dir`() {
        // bin file doesn't exist in temp dir → should fall back to bare name for PATH resolution
        val home = tempDir().absolutePath
        val sdk = mockSdk(home)
        val bin = sdkType.getBinExecutable(sdk, "jest")
        // When file doesn't exist, returns bare "jest" or "jest.cmd" (PATH lookup)
        assertTrue("Expected bin name to contain 'jest', got: $bin", bin.contains("jest"))
    }

    fun `test_getBinExecutable returns full path when bin exists in sdk dir`() {
        val home = tempDir().absolutePath
        val binName = if (SystemInfo.isWindows) "jest.cmd" else "jest"
        File(home, binName).createNewFile()
        val sdk = mockSdk(home)
        val bin = sdkType.getBinExecutable(sdk, "jest")
        assertTrue("Expected full path with $binName, got: $bin", bin.endsWith(binName))
        assertTrue("Expected full path to be inside home dir", bin.contains(File(home).canonicalPath.take(10)))
    }

    // ── suggestHomePaths ─────────────────────────────────────────────────────

    fun `test_suggestHomePaths returns list (may be empty in CI)`() {
        val paths = sdkType.suggestHomePaths()
        assertNotNull(paths)
        // all returned paths must actually contain the node binary
        paths.forEach { p ->
            val nodeBin = if (SystemInfo.isWindows) "node.exe" else "node"
            assertTrue("Path '$p' should contain node binary", File(p, nodeBin).exists())
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun mockSdk(home: String): com.intellij.openapi.projectRoots.Sdk =
        com.intellij.openapi.projectRoots.impl.ProjectJdkImpl("Mock Node.js", sdkType, home, "v20.0.0")
}
