package com.nodespark.testtree

import com.intellij.execution.Executor
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.openapi.project.Project
import com.nodespark.gutter.NodeTestGutterMarkup
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
    // Deliberately not clearing the file's stored results first: a gutter click runs ONE test or
    // one describe, and wiping the file would blank every other group's tick until it was re-run.
    // Nothing goes stale by keeping them — the gutter only draws icons for tests the current parse
    // of the file still finds, so a deleted test cannot show an old result.
    resultsViewer.addEventsListener(object : TestResultsViewer.EventsListener {
        override fun onTestingFinished(viewer: TestResultsViewer) {
            val store = TestResultStore.getInstance(project)
            collect(viewer.testsRootNode ?: return, store)
            NodeTestGutterMarkup.refreshAll(project)
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
        TestResult(
            status = when {
                node.isIgnored -> TestStatus.SKIPPED
                node.isDefect -> TestStatus.FAILED
                else -> TestStatus.PASSED
            },
            failLine = if (node.isDefect) failLineOf(node, file) else null,
            failMessage = node.errorMessage,
        ),
    )
}

/** 'file://C:/p/x.test.js:12:3' — strip only the trailing line/column, never the drive colon. */
private fun fileOf(node: SMTestProxy): String? =
    node.locationUrl?.removePrefix("file://")
        ?.replace(Regex(""":\d+(:\d+)?$"""), "")
        ?.takeIf { it.isNotEmpty() }

/**
 * First stack frame naming the test file itself, not a node_modules/runner internal — the line the
 * assertion actually failed on. Matched by full path, not just the file name: two test files with
 * the same basename in different directories (two `helpers.test.js`, say) would otherwise blame
 * whichever one's stack frame the regex found first.
 */
private fun failLineOf(node: SMTestProxy, file: String): Int? {
    val stacktrace = node.stacktrace?.replace('\\', '/') ?: return null
    return Regex("""${Regex.escape(file)}:(\d+)""").find(stacktrace)?.groupValues?.get(1)?.toIntOrNull()
}
