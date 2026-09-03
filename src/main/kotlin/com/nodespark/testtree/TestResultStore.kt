package com.nodespark.testtree

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

enum class TestStatus { PASSED, FAILED, SKIPPED }

/**
 * Last known result per (absolute forward-slashed file path, test name), fed by
 * [NodeTestConsoleProperties] as a run streams. Read by the gutter to pick a tick/cross icon.
 *
 * ponytail: in-memory only, results vanish on IDE restart — persist to PropertiesComponent if users complain.
 */
@Service(Service.Level.PROJECT)
class TestResultStore {

    private val results = ConcurrentHashMap<Pair<String, String>, TestStatus>()

    fun put(file: String, test: String, status: TestStatus) {
        results[file to test] = status
    }

    fun get(file: String, test: String): TestStatus? = results[file to test]

    /** Drop a file's results when it is about to be re-run, so a deleted test keeps no stale tick. */
    fun clearFile(file: String) {
        results.keys.removeIf { it.first == file }
    }

    companion object {
        fun getInstance(project: Project): TestResultStore = project.service()
    }
}
