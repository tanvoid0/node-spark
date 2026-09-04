package com.nodespark.testtree

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

enum class TestStatus { PASSED, FAILED, SKIPPED }

/** [failLine] is 1-indexed, the line jest/mocha/node:test blamed in the stack trace — null if not found. */
data class TestResult(val status: TestStatus, val failLine: Int? = null, val failMessage: String? = null)

/** Flat, Gson-friendly shape for [TestResultStore]'s persisted map — Gson can't key a JSON object by a Pair. */
private data class StoredResult(
    val file: String,
    val test: String,
    val status: TestStatus,
    val failLine: Int?,
    val failMessage: String?,
)

private const val PROPERTY_KEY = "nodespark.testResultStore"

/**
 * Last known result per (absolute forward-slashed file path, test name), fed by
 * [NodeTestConsoleProperties] as a run streams. Read by the gutter to pick a tick/cross icon.
 *
 * Persisted to [PropertiesComponent] so results survive an IDE restart; written on every [put]
 * since a test run's worth of results is small and PropertiesComponent is in-memory-backed.
 */
@Service(Service.Level.PROJECT)
class TestResultStore(private val project: Project) {

    private val gson = Gson()
    private val results = ConcurrentHashMap<Pair<String, String>, TestResult>().apply { putAll(load()) }

    fun put(file: String, test: String, result: TestResult) {
        results[file to test] = result
        save()
    }

    fun get(file: String, test: String): TestResult? = results[file to test]

    private fun load(): Map<Pair<String, String>, TestResult> {
        val json = PropertiesComponent.getInstance(project).getValue(PROPERTY_KEY) ?: return emptyMap()
        val type = object : TypeToken<List<StoredResult>>() {}.type
        return runCatching { gson.fromJson<List<StoredResult>>(json, type) }
            .getOrNull()
            ?.associate { (it.file to it.test) to TestResult(it.status, it.failLine, it.failMessage) }
            .orEmpty()
    }

    private fun save() {
        val stored = results.entries.map { (key, value) ->
            StoredResult(key.first, key.second, value.status, value.failLine, value.failMessage)
        }
        PropertiesComponent.getInstance(project).setValue(PROPERTY_KEY, gson.toJson(stored))
    }

    companion object {
        fun getInstance(project: Project): TestResultStore = project.service()
    }
}
