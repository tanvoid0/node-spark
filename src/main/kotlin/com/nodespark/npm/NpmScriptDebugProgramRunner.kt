package com.nodespark.npm

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
import com.nodespark.debug.NodeDebugProcess
import java.net.ServerSocket

/**
 * Debugs a package.json script. The script's own node process opens the inspector from the
 * --require hook set up by [NpmScriptRunState]; the package manager that spawned it does not.
 */
class NpmScriptDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {

    override fun getRunnerId() = "NpmScriptDebugProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NpmScriptRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val config = environment.runProfile as NpmScriptRunConfiguration
        val debugPort = ServerSocket(0).use { it.localPort }

        val handler = NpmScriptRunState(environment, config, debugPort)
            .startProcess() as KillableColoredProcessHandler
        ProcessTerminatedListener.attach(handler)

        val session = XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    NodeDebugProcess(session, handler, debugPort)
            })

        handler.startNotify()   // after startSession, or the script runs past the inspector's wait
        return session.runContentDescriptor
    }
}
