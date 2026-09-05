package com.nodespark.npm

import com.intellij.json.psi.JsonFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The grid writes through the PSI so that the rest of the file survives an edit untouched. What is
 * worth pinning down is the punctuation: a removed property must take its comma with it, and must
 * not leave the blank line behind.
 */
class PackageJsonPsiTest : BasePlatformTestCase() {

    private fun edit(text: String, action: (JsonFile) -> Unit): String {
        val file = myFixture.configureByText("package.json", text) as JsonFile
        action(file)
        return file.text
    }

    fun `test removing a middle property takes the comma after it`() {
        val result = edit(
            """
            {
              "name": "demo",
              "version": "1.0.0",
              "license": "MIT"
            }
            """.trimIndent(),
        ) { PackageJsonPsi.write(project, it, listOf("version"), null) }

        assertEquals(
            """
            {
              "name": "demo",
              "license": "MIT"
            }
            """.trimIndent(),
            result,
        )
    }

    fun `test removing the last property takes the comma before it`() {
        val result = edit(
            """
            {
              "name": "demo",
              "license": "MIT"
            }
            """.trimIndent(),
        ) { PackageJsonPsi.write(project, it, listOf("license"), null) }

        assertEquals(
            """
            {
              "name": "demo"
            }
            """.trimIndent(),
            result,
        )
    }

    fun `test changing one value leaves the rest of the file alone`() {
        val result = edit(
            """
            {
              "name": "demo",

              "dependencies": {
                "react": "^18.2.0",
                "zod": "^3.0.0"
              }
            }
            """.trimIndent(),
        ) { PackageJsonPsi.write(project, it, listOf("dependencies", "react"), PackageJsonPsi.string("^19.0.0")) }

        assertEquals(
            """
            {
              "name": "demo",

              "dependencies": {
                "react": "^19.0.0",
                "zod": "^3.0.0"
              }
            }
            """.trimIndent(),
            result,
        )
    }

    fun `test writing into a section that does not exist yet creates it`() {
        val result = edit("""{"name": "demo"}""") {
            PackageJsonPsi.write(project, it, listOf("scripts", "build"), PackageJsonPsi.string("tsc -p ."))
        }
        assertTrue(result, NpmScripts.scriptsOf(result) == mapOf("build" to "tsc -p ."))
        assertEquals("demo", NpmScripts.nameOf(result))
    }

    fun `test renaming a script keeps its command and its place`() {
        val result = edit(
            """
            {
              "scripts": {
                "build": "tsc",
                "test": "vitest run"
              }
            }
            """.trimIndent(),
        ) { PackageJsonPsi.rename(project, it, listOf("scripts", "build"), "compile") }

        assertEquals(
            listOf("compile" to "tsc", "test" to "vitest run"),
            NpmScripts.scriptsOf(result).toList(),
        )
    }

    fun `test a value with a quote in it is escaped`() {
        val result = edit("""{"scripts": {"say": "echo hi"}}""") {
            PackageJsonPsi.write(project, it, listOf("scripts", "say"), PackageJsonPsi.string("""echo "hi""""))
        }
        assertEquals(mapOf("say" to """echo "hi""""), NpmScripts.scriptsOf(result))
    }
}
