package com.nodespark.run

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nodespark.debug.NodeTestDebugProgramRunner
import org.junit.Test

class NodeTestProgramRunnerTest : BasePlatformTestCase() {

    private val runRunner = NodeTestProgramRunner()
    private val debugRunner = NodeTestDebugProgramRunner()

    private fun makeConfig(): NodeTestRunConfiguration {
        val type = NodeTestRunConfigurationType()
        return NodeTestRunConfiguration(project, type.factory, "Test")
    }

    // ── run runner ──────────────────────────────────────────────────────────

    fun `testRunRunnerCanRunNodeTestConfiguration`() {
        assertTrue(runRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, makeConfig()))
    }

    fun `testRunRunnerCannotDebugNodeTestConfiguration`() {
        assertFalse(runRunner.canRun(DefaultDebugExecutor.EXECUTOR_ID, makeConfig()))
    }

    fun `testRunRunnerHasCorrectId`() {
        assertEquals("NodeTestProgramRunner", runRunner.runnerId)
    }

    // ── debug runner ────────────────────────────────────────────────────────

    fun `testDebugRunnerCanDebugNodeTestConfiguration`() {
        assertTrue(debugRunner.canRun(DefaultDebugExecutor.EXECUTOR_ID, makeConfig()))
    }

    fun `testDebugRunnerCannotRunNodeTestConfiguration`() {
        assertFalse(debugRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, makeConfig()))
    }

    fun `testDebugRunnerHasCorrectId`() {
        assertEquals("NodeTestDebugProgramRunner", debugRunner.runnerId)
    }

    // ── canRun rejects other profile types ──────────────────────────────────

    fun `testRunRunnerRejectsUnknownProfile`() {
        val otherProfile = object : com.intellij.execution.configurations.RunProfile {
            override fun getState(e: com.intellij.execution.Executor, env: com.intellij.execution.runners.ExecutionEnvironment) = null
            override fun getName() = "other"
            override fun getIcon() = null
        }
        assertFalse(runRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, otherProfile))
        assertFalse(debugRunner.canRun(DefaultDebugExecutor.EXECUTOR_ID, otherProfile))
    }
}
