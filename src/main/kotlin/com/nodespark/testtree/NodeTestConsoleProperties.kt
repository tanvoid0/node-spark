package com.nodespark.testtree

import com.intellij.execution.Executor
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.openapi.project.Project
import com.nodespark.run.NodeTestRunConfiguration

/**
 * SM test-tree properties for a Node test run. The bundled reporters emit TeamCity service messages
 * themselves, so nothing transcodes output here.
 */
class NodeTestConsoleProperties(
    config: NodeTestRunConfiguration,
    executor: Executor,
) : SMTRunnerConsoleProperties(config, "NodeSpark", executor) {

    init {
        // The reporters address nodes by nodeId/parentNodeId, which the platform only honours in
        // id-based mode; without it every message is matched by name and the tree collapses.
        isIdBasedTestTree = true
    }
}

/**
 * Feeds finished runs into [TestResultStore] so the editor gutter can show a tick or a cross.
 * Reading the finished tree once is enough — the gutter is not repainted mid-run anyway.
 */
fun SMTRunnerConsoleView.recordResultsInto(project: Project, testFilePath: String) {
    TestResultStore.getInstance(project).clearFile(testFilePath.replace('\\', '/'))
    resultsViewer.addEventsListener(object : TestResultsViewer.EventsListener {
        override fun onTestingFinished(viewer: TestResultsViewer) {
            val store = TestResultStore.getInstance(project)
            collect(viewer.testsRootNode ?: return, store)
        }
    })
}

private fun collect(node: SMTestProxy, store: TestResultStore) {
    for (child in node.children) collect(child, store)
    if (!node.isLeaf) return
    val file = fileOf(node) ?: return
    store.put(
        file,
        node.name,
        when {
            node.isIgnored -> TestStatus.SKIPPED
            node.isDefect -> TestStatus.FAILED
            else -> TestStatus.PASSED
        },
    )
}

/** 'file://C:/p/x.test.js:12:3' — strip only the trailing line/column, never the drive colon. */
private fun fileOf(node: SMTestProxy): String? =
    node.locationUrl?.removePrefix("file://")
        ?.replace(Regex(""":\d+(:\d+)?$"""), "")
        ?.takeIf { it.isNotEmpty() }
