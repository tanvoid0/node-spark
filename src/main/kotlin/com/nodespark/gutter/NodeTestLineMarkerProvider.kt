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

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null

        val file = element.containingFile?.virtualFile ?: return null
        if (!NodeTestDetector.isTestFile(file)) return null

        val document = PsiDocumentManager.getInstance(element.project)
            .getDocument(element.containingFile) ?: return null

        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)

        val textBefore = document.getText(TextRange(lineStart, offset))
        if (textBefore.isNotBlank()) return null

        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))

        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(lineText)
        if (!matcher.find()) return null

        val testName = matcher.group(5) ?: return null

        // Show the last known result for this test, falling back to the plain run arrow
        val icon = when (TestResultStore.getInstance(element.project).get(file.path, testName)) {
            TestStatus.PASSED -> AllIcons.RunConfigurations.TestState.Green2
            TestStatus.FAILED -> AllIcons.RunConfigurations.TestState.Red2
            TestStatus.SKIPPED -> AllIcons.RunConfigurations.TestState.Yellow2
            null -> NodeSparkIcons.RunTest
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { "Left-click: Run  |  Right-click: Debug — $testName" },
            { e, el -> onGutterClick(e, el, file.path, testName) },
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
