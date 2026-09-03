package com.nodespark.noderun

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.nodespark.debug.NodeDebugProcess
import java.net.ServerSocket

class NodeAppProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeAppProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is NodeAppRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }
}

/** Runs the script under --inspect-brk and hands the process to the shared CDP debug process. */
class NodeAppDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeAppDebugProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NodeAppRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val config = environment.runProfile as NodeAppRunConfiguration
        val debugPort = ServerSocket(0).use { it.localPort }

        // NodeDebugProcess builds its own console, so bypass state.execute() entirely.
        val processHandler = NodeAppRunState(environment, config, debugPort).startProcess()

        val session = XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    NodeDebugProcess(session, processHandler, debugPort)
            })

        processHandler.startNotify()   // must be after startSession, or node runs past --inspect-brk
        return session.runContentDescriptor
    }
}
