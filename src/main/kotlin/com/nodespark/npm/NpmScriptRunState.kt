package com.nodespark.npm

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
import com.nodespark.util.NodePackageManager
import java.io.File

class NpmScriptRunState(
    env: ExecutionEnvironment,
    private val config: NpmScriptRunConfiguration,
) : CommandLineState(env) {

    private val project: Project = env.project

    public override fun startProcess(): ProcessHandler {
        val handler = KillableColoredProcessHandler(buildCommandLine())
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun createConsole(executor: Executor): ConsoleView =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    private fun buildCommandLine(): GeneralCommandLine {
        val pkgDir = config.workingDir.ifBlank {
            File(config.packageJsonPath).parent ?: project.basePath ?: System.getProperty("user.home")
        }
        val pm = NodePackageManager.detect(pkgDir)

        // A package manager is always the exe, never an argument to node. resolveBin falls back to the bare
        // name when nothing is on disk; on Windows that must become "npm.cmd" or CreateProcess fails with error=2.
        val bin = NodeCommandLine.resolveBin(project, pkgDir, pm.binName, config.packageJsonPath)
            .let { if (it == pm.binName) NodeCommandLine.onPath(pm.binary()) else it }

        val cmd = NodeCommandLine.base(project, pkgDir, NodeCommandLine.parseEnvVars(config.envVars))
        cmd.exePath = bin
        cmd.addParameters(pm.runArgs(config.scriptName))

        if (config.arguments.isNotBlank()) {
            // only npm needs the separator; yarn/pnpm/bun pass trailing args straight through
            if (pm == NodePackageManager.NPM) cmd.addParameter("--")
            cmd.addParameters(ParametersListUtil.parse(config.arguments))
        }
        return cmd
    }
}
