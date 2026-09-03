package com.nodespark.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor

class NodeTestProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeTestProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is NodeTestRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }
}
