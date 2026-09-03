package com.nodespark.util

import com.intellij.openapi.diagnostic.logger
import java.io.File

/**
 * Resolves a test runner's real JavaScript entry point, so we can spawn
 * `node <entry.js>` instead of the `node_modules/.bin/<name>.cmd` shim.
 *
 * This is not a style preference. Launching through the Windows `.cmd` shim routes arguments
 * through cmd.exe, which corrupts them in ways that silently break test filtering:
 * a `^` is eaten, `&` splits the command, `>` redirects and creates a stray file, and `%FOO%`
 * is expanded to the environment variable's value even inside quotes. GeneralCommandLine's
 * quoting rescues the first three but NOT `%FOO%` — so a test named `covers %PATH% expansion`
 * would be mangled before the runner ever sees it. Spawning node directly passes every argument
 * through byte for byte.
 */
object NodeRunnerEntry {

    private val LOG = logger<NodeRunnerEntry>()

    /**
     * The package directory and entry-relative path for each runner's CLI.
     *
     * Mocha deliberately targets `_mocha` rather than `mocha.js`: the latter is a wrapper that
     * re-spawns a child node process when node flags are present, which would break `--inspect-brk`
     * port attachment and confuse exit-code handling.
     */
    private val BIN_NAME = mapOf(
        NodeTestDetector.TestRunner.JEST to "jest",
        NodeTestDetector.TestRunner.VITEST to "vitest",
        NodeTestDetector.TestRunner.MOCHA to "_mocha",
    )

    private val PACKAGE_NAME = mapOf(
        NodeTestDetector.TestRunner.JEST to listOf("jest", "jest-cli"),
        NodeTestDetector.TestRunner.VITEST to listOf("vitest"),
        NodeTestDetector.TestRunner.MOCHA to listOf("mocha"),
    )

    /**
     * Absolute path to the runner's entry script, or null when it cannot be found.
     * [startDir] is the directory to begin the node_modules walk from — normally the directory
     * holding the test file, so that workspace/monorepo packages resolve against their own
     * dependencies before the workspace root.
     */
    fun resolve(runner: NodeTestDetector.TestRunner, startDir: File, projectRoot: File?): String? {
        if (runner == NodeTestDetector.TestRunner.NODE_TEST) return null // `node --test`, no entry script
        val binName = BIN_NAME[runner] ?: return null
        val entry = resolveEntry(PACKAGE_NAME[runner].orEmpty(), binName, startDir, projectRoot)
        if (entry == null) LOG.debug("No entry point found for $runner from $startDir")
        return entry
    }

    /**
     * The same node_modules walk for any CLI package: the first of [packages] whose package.json
     * declares a `bin` entry named [binName], falling back to reading the Windows shim.
     */
    fun resolveEntry(packages: List<String>, binName: String, startDir: File, projectRoot: File?): String? {
        for (nodeModules in nodeModulesChain(startDir, projectRoot)) {
            for (pkg in packages) {
                fromPackageJson(File(nodeModules, pkg), binName)?.let { return it }
            }
            fromCmdShim(File(nodeModules, ".bin"), binName)?.let { return it }
        }
        return null
    }

    /** Every `node_modules` directory from [startDir] upwards, nearest first. */
    private fun nodeModulesChain(startDir: File, projectRoot: File?): List<File> {
        val dirs = mutableListOf<File>()
        var dir: File? = startDir.absoluteFile
        while (dir != null) {
            val nm = File(dir, "node_modules")
            if (nm.isDirectory) dirs.add(nm)
            dir = dir.parentFile
        }
        // The project root may sit outside the test file's ancestry (rare, but possible with
        // symlinked content roots), so make sure it is considered too.
        projectRoot?.let {
            val nm = File(it, "node_modules")
            if (nm.isDirectory && dirs.none { d -> d.absolutePath == nm.absolutePath }) dirs.add(nm)
        }
        return dirs
    }

    /**
     * Reads `<pkgDir>/package.json` and picks the `bin` entry for [binName].
     * `bin` is either a string (keyed by the package's own name) or an object of name -> path.
     * The result is canonicalised so pnpm's symlinked layout resolves to the real file.
     */
    private fun fromPackageJson(pkgDir: File, binName: String): String? {
        val manifest = File(pkgDir, "package.json")
        if (!manifest.isFile) return null
        val json = runCatching { manifest.readText() }.getOrNull() ?: return null

        val relative = binStringValue(json) ?: binObjectValue(json, binName) ?: return null
        val entry = File(pkgDir, relative.removePrefix("./"))
        if (!entry.isFile) return null
        return runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath }
    }

    /** Matches `"bin": "./bin/jest.js"` — the string form, where the key is the package name. */
    private fun binStringValue(json: String): String? =
        Regex("\"bin\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    /** Matches `"bin": { "_mocha": "./bin/_mocha", ... }` — pick the entry for [binName]. */
    private fun binObjectValue(json: String, binName: String): String? {
        val obj = Regex("\"bin\"\\s*:\\s*\\{([^}]*)}", RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1) ?: return null
        return Regex("\"" + Regex.escape(binName) + "\"\\s*:\\s*\"([^\"]+)\"")
            .find(obj)?.groupValues?.get(1)
    }

    /**
     * Fallback: the Windows shim literally contains the path to the real entry script.
     * npm writes `"%dp0%\..\jest\bin\jest.js"`; pnpm writes a `.pnpm/...` path and, unlike npm,
     * names the file with an uppercase extension (`jest.CMD`), hence the case-insensitive probe.
     */
    private fun fromCmdShim(binDir: File, binName: String): String? {
        if (!binDir.isDirectory) return null
        val shim = listOf("$binName.cmd", "$binName.CMD")
            .map { File(binDir, it) }
            .firstOrNull { it.isFile }
            ?: return null

        val text = runCatching { shim.readText() }.getOrNull() ?: return null
        val match = Regex("""["']?%[~]?dp0%[\\/]([^"'\s]+\.(?:js|mjs|cjs))["']?""", RegexOption.IGNORE_CASE)
            .find(text) ?: return null

        val entry = File(binDir, match.groupValues[1])
        if (!entry.isFile) return null
        return runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath }
    }
}
