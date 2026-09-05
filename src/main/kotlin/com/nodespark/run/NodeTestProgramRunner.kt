package com.nodespark.run

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageExecutor
import com.intellij.coverage.CoverageHelper
import com.intellij.coverage.CoverageRunnerData
import com.intellij.execution.configurations.ConfigurationInfoProvider
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration

class NodeTestProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeTestProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == CoverageExecutor.EXECUTOR_ID) &&
            profile is NodeTestRunConfiguration

    // Without this the run carries no RunnerSettings and CoverageDataManager ignores the finished
    // process. Handing it out unconditionally is what the platform's own Java runner does — nothing
    // reads it unless the run also registered a suite below.
    override fun createConfigurationData(settingsProvider: ConfigurationInfoProvider): RunnerSettings =
        CoverageRunnerData()

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        val config = environment.runProfile as? NodeTestRunConfiguration
        if (config != null && environment.executor.id == CoverageExecutor.EXECUTOR_ID) {
            // The suite has to exist before the process ends: CoverageDataManager reads it back off
            // the configuration when the handler terminates, and does nothing if it is not there.
            val enabled = CoverageEnabledConfiguration.getOrCreate(config)
            enabled.currentCoverageSuite = CoverageDataManager.getInstance(config.project).addCoverageSuite(enabled)
            CoverageHelper.attachToProcess(config, executionResult.processHandler, environment.runnerSettings)
        }
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }
}
