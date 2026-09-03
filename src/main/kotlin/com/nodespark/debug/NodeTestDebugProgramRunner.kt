package com.nodespark.debug

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.nodespark.run.NodeTestRunConfiguration
import com.nodespark.run.NodeTestRunState
import java.net.ServerSocket

class NodeTestDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeTestDebugProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NodeTestRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val config = environment.runProfile as NodeTestRunConfiguration
        val debugPort = findFreePort()

        val debugState = NodeTestRunState(environment, config, debugPort)
        val processHandler = debugState.startProcess() as KillableColoredProcessHandler
        ProcessTerminatedListener.attach(processHandler)

        val session = XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    NodeDebugProcess(session, processHandler, debugPort)
            })

        processHandler.startNotify()
        return session.runContentDescriptor
    }

    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
}
