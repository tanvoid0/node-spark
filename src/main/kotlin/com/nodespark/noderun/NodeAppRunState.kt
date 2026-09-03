package com.nodespark.noderun

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeProjectUtil

class NodeAppRunState(
    env: ExecutionEnvironment,
    private val config: NodeAppRunConfiguration,
    private val debugPort: Int = -1,   // -1 = normal run, >0 = debug mode
) : CommandLineState(env) {

    private val project: Project = env.project

    // Widened to public so the debug runner can build the handler itself.
    // Do NOT override execute() — that causes a premature startNotify().
    public override fun startProcess(): ProcessHandler {
        val handler = KillableColoredProcessHandler(buildCommandLine())
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun createConsole(executor: Executor): ConsoleView =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    private fun buildCommandLine(): GeneralCommandLine {
        val workDir = config.workingDir.ifBlank { NodeProjectUtil.projectRootFor(project, config.scriptPath) }
        val cmd = NodeCommandLine.base(project, workDir, NodeCommandLine.parseEnvVars(config.envVars))
        cmd.exePath = NodeCommandLine.nodeExecutable(project, config.scriptPath)
        cmd.addParameters(argv(config.nodeOptions, config.scriptPath, config.applicationArgs, debugPort))
        return cmd
    }

    companion object {
        /** Pure argument assembly: debug flag, node options, script, program args. */
        @JvmStatic
        @JvmOverloads
        fun argv(
            nodeOptions: String,
            scriptPath: String,
            appArgs: String,
            debugPort: Int = -1,
        ): List<String> = buildList {
            if (debugPort > 0) add("--inspect-brk=$debugPort")
            addAll(ParametersListUtil.parse(nodeOptions))
            add(scriptPath)
            addAll(ParametersListUtil.parse(appArgs))
        }
    }
}
