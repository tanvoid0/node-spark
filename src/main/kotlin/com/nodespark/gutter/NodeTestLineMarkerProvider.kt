package com.nodespark.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.testtree.TestStatus
import com.nodespark.testtree.TestResultStore
import com.nodespark.run.NodeTestConfigurationUtil
import com.nodespark.run.NodeTestRunConfiguration
import com.nodespark.util.NodeTestDetector
import java.awt.event.MouseEvent

class NodeTestLineMarkerProvider : LineMarkerProvider {

    // Markers are produced per line, not per leaf — see collectSlowLineMarkers.
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    /**
     * IntelliJ IDEA Community has no JavaScript plugin, so a .js file there is plain text: its PSI
     * is a single leaf spanning the whole file rather than one leaf per token. Anchoring a marker
     * to the line its leaf happens to start on therefore finds at most one test per file — and in
     * practice none, because that one line is the top of the file.
     *
     * ponytail: line-based scan fixes the plain-text PSI case, but the icons are still missing in
     * an IDE where TextMate owns .js (see docs/known-issues.md) - marking the editor up directly
     * is the upgrade path if a PSI pass turns out never to run there.
     *
     * So the scan is by line instead of by leaf: every line whose start offset falls inside a
     * given leaf is checked against the test pattern. A line start belongs to exactly one leaf, so
     * no line is visited twice, and the same code covers both PSI shapes — a token-per-leaf file
     * from a JavaScript plugin and a whole-file leaf without one.
     */
    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val first = elements.firstOrNull() ?: return
        val psiFile = first.containingFile ?: return
        val file = psiFile.virtualFile ?: return
        if (!NodeTestDetector.isTestFile(file)) return

        val document = PsiDocumentManager.getInstance(first.project).getDocument(psiFile) ?: return

        for (element in elements) {
            if (element.firstChild != null) continue
            val range = element.textRange ?: continue

            val firstLine = document.getLineNumber(range.startOffset)
            val lastLine = document.getLineNumber(range.endOffset.coerceAtMost(document.textLength))

            for (line in firstLine..lastLine) {
                val lineStart = document.getLineStartOffset(line)
                if (lineStart < range.startOffset || lineStart >= range.endOffset) continue

                val lineText = document.getText(TextRange(lineStart, document.getLineEndOffset(line)))
                val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(lineText)
                if (!matcher.find()) continue
                val testName = matcher.group(5) ?: continue

                result.add(marker(element, lineStart, lineText, file.path, testName))
            }
        }
    }

    private fun marker(
        element: PsiElement,
        lineStart: Int,
        lineText: String,
        filePath: String,
        testName: String,
    ): LineMarkerInfo<PsiElement> {
        // Show the last known result for this test, falling back to the plain run arrow
        val icon = when (TestResultStore.getInstance(element.project).get(filePath, testName)) {
            TestStatus.PASSED -> AllIcons.RunConfigurations.TestState.Green2
            TestStatus.FAILED -> AllIcons.RunConfigurations.TestState.Red2
            TestStatus.SKIPPED -> AllIcons.RunConfigurations.TestState.Yellow2
            null -> NodeSparkIcons.RunTest
        }

        // The icon sits on the line's first non-blank character, clamped to the leaf that owns the
        // line: a marker range outside its own element is not a range the daemon will accept.
        val indent = lineText.takeWhile { it.isWhitespace() }.length
        val own = element.textRange
        val anchor = TextRange(
            (lineStart + indent).coerceIn(own.startOffset, own.endOffset),
            (lineStart + lineText.length).coerceIn(own.startOffset, own.endOffset),
        )

        return LineMarkerInfo(
            element,
            anchor,
            icon,
            { "Left-click: Run  |  Right-click: Debug — $testName" },
            { e, el -> onGutterClick(e, el, filePath, testName) },
            GutterIconRenderer.Alignment.LEFT,
            { "Run / Debug: $testName" }
        )
    }

    private fun onGutterClick(event: MouseEvent, element: PsiElement, filePath: String, testName: String) {
        // Right-click or Ctrl+click → debug; plain left-click → run
        if (event.button == MouseEvent.BUTTON3 || event.isControlDown) {
            debugTest(element, filePath, testName)
        } else {
            runTest(element, filePath, testName)
        }
    }

    private fun buildConfig(element: PsiElement, filePath: String, testName: String, label: String): com.intellij.execution.RunnerAndConfigurationSettings? {
        val project = element.project
        val settings = RunManager.getInstance(project)
            .createConfiguration(label, NodeTestConfigurationUtil.getType().factory)
        val config = settings.configuration as? NodeTestRunConfiguration ?: return null
        config.testFilePath = filePath
        config.testNameFilter = testName
        config.workingDir = project.basePath ?: ""
        // Temporary, not addConfiguration: a gutter click shouldn't leave a permanent entry in Run/Debug.
        RunManager.getInstance(project).setTemporaryConfiguration(settings)
        return settings
    }

    private fun runTest(element: PsiElement, filePath: String, testName: String) {
        val settings = buildConfig(element, filePath, testName, "▶ $testName") ?: return
        val executor = ExecutorRegistry.getInstance()
            .getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return
        ExecutionUtil.runConfiguration(settings, executor)
    }

    private fun debugTest(element: PsiElement, filePath: String, testName: String) {
        val settings = buildConfig(element, filePath, testName, "⬡ $testName") ?: return
        val executor = ExecutorRegistry.getInstance()
            .getExecutorById(DefaultDebugExecutor.EXECUTOR_ID) ?: return
        ExecutionUtil.runConfiguration(settings, executor)
    }
}
