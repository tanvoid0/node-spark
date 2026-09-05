package com.nodespark.util

import com.intellij.openapi.application.PathManager
import java.io.File

/**
 * The bundled TeamCity reporters live inside the plugin jar, but a test runner can only load a
 * reporter from a real path on disk, so they are unpacked once into the IDE system directory.
 * `tc.js` must land beside them — every reporter does `require('./tc.js')`.
 */
object NodeReporters {

    private val FILES =
        listOf("tc.js", "jest.cjs", "vitest.mjs", "mocha.cjs", "node-test.mjs", "inspect-bootstrap.cjs")

    private val dir: File by lazy {
        val target = File(PathManager.getSystemPath(), "nodespark/reporters")
        target.mkdirs()
        for (name in FILES) {
            val bytes = javaClass.getResourceAsStream("/nodespark/reporters/$name")?.use { it.readBytes() }
                ?: continue
            val out = File(target, name)
            // Rewrite whenever the content differs, so a plugin upgrade does not keep serving a stale copy.
            if (!out.isFile || !out.readBytes().contentEquals(bytes)) out.writeBytes(bytes)
        }
        target
    }

    /** Absolute path of a bundled reporter, or null when it failed to unpack. */
    fun path(runner: NodeTestDetector.TestRunner): String? {
        val name = when (runner) {
            NodeTestDetector.TestRunner.JEST -> "jest.cjs"
            NodeTestDetector.TestRunner.VITEST -> "vitest.mjs"
            NodeTestDetector.TestRunner.MOCHA -> "mocha.cjs"
            NodeTestDetector.TestRunner.NODE_TEST -> "node-test.mjs"
        }
        return File(dir, name).takeIf { it.isFile }?.absolutePath
    }

    /**
     * Absolute path of the `--require` hook that opens the inspector in the first non-package-manager
     * node process of a `npm run` tree. Null when it failed to unpack.
     */
    fun inspectBootstrap(): String? =
        File(dir, "inspect-bootstrap.cjs").takeIf { it.isFile }?.absolutePath
}
