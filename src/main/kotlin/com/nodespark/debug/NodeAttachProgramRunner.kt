package com.nodespark.debug

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.DefaultDebugProcessHandler
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.nodespark.attach.NodeAttachRunConfiguration

class NodeAttachProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NodeAttachProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NodeAttachRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val config = environment.runProfile as NodeAttachRunConfiguration
        val port = config.port.trim().toIntOrNull() ?: return null

        // Inert stand-in for the process we did not spawn: destroyProcess() only fires
        // notifyProcessTerminated(0), and detachIsDefault() gives the tab a Detach button.
        val handler = DefaultDebugProcessHandler()

        val session = XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    NodeDebugProcess(session, handler, port, config.host.trim())
            })

        handler.startNotify()   // without this a later detachProcess() is queued forever
        return session.runContentDescriptor
    }
}
