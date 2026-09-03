package com.nodespark.util

import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

object NodeTestDetector {

    private val TEST_FILE_PATTERNS = listOf(
        Regex(""".*\.(test|spec)\.(js|ts|mjs|mts|cjs|cts)$"""),
        Regex(""".*/__tests__/.*\.(js|ts|mjs|mts|cjs|cts)$"""),
    )

    // Matches: test('name', ...), it('name', ...), describe('name', ...)
    // Also: test.each, it.only, describe.skip, etc.
    val TEST_FUNCTION_PATTERN: Pattern = Pattern.compile(
        """^\s*(export\s+)?(describe|it|test)(\.(?:only|skip|each|todo|concurrent))?\s*\(\s*(['"`])(.+?)\4""",
        Pattern.MULTILINE
    )

    fun isTestFile(file: VirtualFile): Boolean {
        if (!isJsOrTs(file)) return false
        return TEST_FILE_PATTERNS.any { it.matches(file.path) }
    }

    fun isJsOrTs(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in setOf("js", "ts", "mjs", "mts", "cjs", "cts", "jsx", "tsx")
    }

    /**
     * Precedence matters: a project that has jest as its `test` script commonly also has vitest
     * installed (or vice versa), so probing `node_modules/.bin` first picks the wrong runner.
     * The declared intent — the test script, then the declared dependency, then a config file —
     * beats what merely happens to be installed.
     */
    fun detectRunner(projectDir: String): TestRunner {
        val base = java.io.File(projectDir)
        val pkg = java.io.File(base, "package.json").takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }

        pkg?.let { json ->
            fromTestScript(json)?.let { return it }
            fromDependencies(json)?.let { return it }
        }
        fromConfigFile(base)?.let { return it }
        fromBinDir(base)?.let { return it }
        return TestRunner.NODE_TEST   // built in, needs nothing installed
    }

    /** The first runner named in the `test` script wins — that is what the user runs by hand. */
    private fun fromTestScript(json: String): TestRunner? {
        val scripts = Regex(""""scripts"\s*:\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1) ?: return null
        val test = Regex(""""test"\s*:\s*"([^"]*)"""").find(scripts)?.groupValues?.get(1) ?: return null
        if (Regex("""\bnode\b[^&|]*--test\b""").containsMatchIn(test)) return TestRunner.NODE_TEST
        return NAMED.firstOrNull { Regex("""\b${it.binName}\b""").containsMatchIn(test) }
    }

    /** Only decisive when exactly one of the three is declared; otherwise fall through. */
    private fun fromDependencies(json: String): TestRunner? {
        val deps = Regex(""""(dev|peer|optional)?[dD]ependencies"\s*:\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(json).joinToString("\n") { it.groupValues[2] }
        return NAMED.filter { Regex(""""${it.binName}"\s*:""").containsMatchIn(deps) }.singleOrNull()
    }

    private fun fromConfigFile(base: java.io.File): TestRunner? {
        val names = base.list()?.toSet() ?: return null
        fun has(prefix: String) = names.any { it.startsWith(prefix) }
        return when {
            has("vitest.config") || has("vitest.workspace") -> TestRunner.VITEST
            has("jest.config") -> TestRunner.JEST
            has(".mocharc") -> TestRunner.MOCHA
            else -> null
        }
    }

    private fun fromBinDir(base: java.io.File): TestRunner? {
        val bin = java.io.File(base, "node_modules/.bin")
        return NAMED.firstOrNull {
            java.io.File(bin, it.binName).exists() || java.io.File(bin, "${it.binName}.cmd").exists()
        }
    }

    private val NAMED = listOf(TestRunner.JEST, TestRunner.VITEST, TestRunner.MOCHA)

    enum class TestRunner(val binName: String) {
        JEST("jest"),
        VITEST("vitest"),
        MOCHA("mocha"),
        NODE_TEST("node"),
    }
}
