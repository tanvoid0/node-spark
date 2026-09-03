package com.nodespark.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * The gutter icons have to work in IntelliJ IDEA Community, which has no JavaScript plugin: a .js
 * file there is plain text, so its PSI is a single leaf spanning the whole file rather than one
 * leaf per token. A provider that only reports on the line its leaf starts therefore sees exactly
 * one line, and every test below the first is left without an icon.
 *
 * The fixture reproduces that faithfully — the test platform has no JavaScript plugin either.
 * The provider is called directly rather than through doHighlighting(), which drags in the ESLint
 * and Prettier annotators and their real filesystem.
 */
class NodeTestLineMarkerProviderTest : BasePlatformTestCase() {

    private val source = """
        const { add } = require('./math');

        describe('add', () => {
          test('adds two numbers', () => {
            expect(add(2, 3)).toBe(5);
          });

          it('adds zero', () => {
            expect(add(5, 0)).toBe(5);
          });
        });
    """.trimIndent()

    /** What the daemon does: hand every leaf to the provider and collect what comes back. */
    private fun markerLines(file: PsiFile): List<String> {
        val provider = NodeTestLineMarkerProvider()
        val document = file.viewProvider.document!!
        val leaves = PsiTreeUtil.collectElements(file) { it.firstChild == null }.toList()
        val markers = mutableListOf<LineMarkerInfo<*>>()
        provider.collectSlowLineMarkers(leaves, markers)
        return markers
            .map { info ->
                val line = document.getLineNumber(info.startOffset)
                document.getText(
                    com.intellij.openapi.util.TextRange(
                        document.getLineStartOffset(line),
                        document.getLineEndOffset(line),
                    ),
                ).trim()
            }
    }

    @Test
    fun `testEveryTestLineGetsAMarkerInAPlainTextFile`() {
        val file = myFixture.configureByText("math.test.js", source)

        val lines = markerLines(file)

        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("describe('add'"))
        assertTrue(lines[1].startsWith("test('adds two numbers'"))
        assertTrue(lines[2].startsWith("it('adds zero'"))
    }

    @Test
    fun `testNoMarkersInAFileThatIsNotATestFile`() {
        val file = myFixture.configureByText("math.js", source)

        assertEmpty(markerLines(file))
    }
}
