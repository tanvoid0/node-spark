package com.nodespark.util

import com.nodespark.tempDir
import org.junit.Assert.*
import org.junit.Test

class NodeTestDetectorTest {

    // ── isTestFile ──────────────────────────────────────────────────────────

    @Test fun `test file dot test dot js detected`() {
        assertTrue(isTestFile("foo.test.js"))
    }

    @Test fun `test file dot spec dot js detected`() {
        assertTrue(isTestFile("foo.spec.js"))
    }

    @Test fun `test file dot test dot ts detected`() {
        assertTrue(isTestFile("foo.test.ts"))
    }

    @Test fun `test file dot spec dot ts detected`() {
        assertTrue(isTestFile("foo.spec.ts"))
    }

    @Test fun `test file dot test dot mjs detected`() {
        assertTrue(isTestFile("foo.test.mjs"))
    }

    @Test fun `test file dot test dot cjs detected`() {
        assertTrue(isTestFile("foo.test.cjs"))
    }

    @Test fun `__tests__ directory detected`() {
        assertTrue(isTestFile("src/__tests__/utils.js"))
    }

    @Test fun `plain js file not detected`() {
        assertFalse(isTestFile("foo.js"))
    }

    @Test fun `plain ts file not detected`() {
        assertFalse(isTestFile("foo.ts"))
    }

    @Test fun `json file not detected`() {
        assertFalse(isTestFile("foo.json"))
    }

    @Test fun `test in filename without extension not detected`() {
        assertFalse(isTestFile("test"))
    }

    // ── TEST_FUNCTION_PATTERN ───────────────────────────────────────────────

    @Test fun `matches test() declaration`() {
        val line = "test('adds numbers', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `matches it() declaration`() {
        val line = "  it('should work', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `matches describe() declaration`() {
        val line = "describe('Math', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `matches test dot only`() {
        val line = "test.only('focused', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `matches it dot skip`() {
        val line = "it.skip('skipped', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `captures test name group 5`() {
        val line = "test('my test name', () => {"
        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line)
        assertTrue(matcher.find())
        assertEquals("my test name", matcher.group(5))
    }

    @Test fun `captures describe name group 5`() {
        val line = "describe('MyClass', () => {"
        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line)
        assertTrue(matcher.find())
        assertEquals("MyClass", matcher.group(5))
    }

    @Test fun `matches double-quoted test name`() {
        val line = """test("double quotes", () => {"""
        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line)
        assertTrue(matcher.find())
        assertEquals("double quotes", matcher.group(5))
    }

    @Test fun `matches backtick test name`() {
        val line = "test(`template literal`, () => {"
        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line)
        assertTrue(matcher.find())
        assertEquals("template literal", matcher.group(5))
    }

    @Test fun `does not match function call named test`() {
        // not a test declaration — just a variable named 'test'
        val line = "const test = getValue();"
        assertFalse(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    @Test fun `matches exported test`() {
        val line = "export test('exported test', () => {"
        assertTrue(NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(line).find())
    }

    // ── detectRunner ────────────────────────────────────────────────────────

    @Test fun `detect runner falls back to node --test when nothing is installed`() {
        val emptyDir = tempDir()
        val runner = NodeTestDetector.detectRunner(emptyDir.absolutePath)
        assertEquals(NodeTestDetector.TestRunner.NODE_TEST, runner)
        emptyDir.deleteRecursively()
    }

    @Test fun `detect runner finds jest when jest bin exists`() {
        val dir = tempDir()
        val bin = java.io.File(dir, "node_modules/.bin")
        bin.mkdirs()
        java.io.File(bin, "jest").createNewFile()
        val runner = NodeTestDetector.detectRunner(dir.absolutePath)
        assertEquals(NodeTestDetector.TestRunner.JEST, runner)
        dir.deleteRecursively()
    }

    @Test fun `test script wins over an installed bin`() {
        // The real-world case this fixes: vitest is installed as a transitive dep but the project
        // actually runs jest, and probing node_modules/.bin alone picked vitest.
        val dir = tempDir()
        val bin = java.io.File(dir, "node_modules/.bin")
        bin.mkdirs()
        java.io.File(bin, "vitest").createNewFile()
        java.io.File(dir, "package.json").writeText(
            """{"scripts":{"test":"jest --ci"},"devDependencies":{"jest":"^29","vitest":"^1"}}"""
        )
        assertEquals(NodeTestDetector.TestRunner.JEST, NodeTestDetector.detectRunner(dir.absolutePath))
        dir.deleteRecursively()
    }

    @Test fun `a single declared dependency wins when there is no test script`() {
        val dir = tempDir()
        java.io.File(dir, "package.json").writeText("""{"devDependencies":{"vitest":"^1"}}""")
        assertEquals(NodeTestDetector.TestRunner.VITEST, NodeTestDetector.detectRunner(dir.absolutePath))
        dir.deleteRecursively()
    }

    @Test fun `config file breaks a two-dependency tie`() {
        val dir = tempDir()
        java.io.File(dir, "package.json").writeText("""{"devDependencies":{"jest":"^29","vitest":"^1"}}""")
        java.io.File(dir, "vitest.config.ts").createNewFile()
        assertEquals(NodeTestDetector.TestRunner.VITEST, NodeTestDetector.detectRunner(dir.absolutePath))
        dir.deleteRecursively()
    }

    @Test fun `node --test script is recognised`() {
        val dir = tempDir()
        java.io.File(dir, "package.json").writeText("""{"scripts":{"test":"node --test src/"}}""")
        assertEquals(NodeTestDetector.TestRunner.NODE_TEST, NodeTestDetector.detectRunner(dir.absolutePath))
        dir.deleteRecursively()
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun isTestFile(path: String): Boolean {
        val patterns = listOf(
            Regex(""".*\.(test|spec)\.(js|ts|mjs|mts|cjs|cts)$"""),
            Regex(""".*/__tests__/.*\.(js|ts|mjs|mts|cjs|cts)$"""),
        )
        return patterns.any { it.matches(path) }
    }
}
