package com.nodespark.npm

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import java.io.File

/** Creates (or reuses) a run configuration for a package.json script and starts it. */
object NpmScriptRunner {

    @JvmOverloads
    fun run(
        project: Project,
        packageJsonPath: String,
        script: String,
        executorId: String = DefaultRunExecutor.EXECUTOR_ID,
    ) {
        val runManager = RunManager.getInstance(project)
        val existing = runManager.allSettings.firstOrNull {
            val config = it.configuration as? NpmScriptRunConfiguration ?: return@firstOrNull false
            config.packageJsonPath == packageJsonPath && config.scriptName == script
        }
        val settings = existing ?: runManager
            .createConfiguration(script, NpmScriptConfigurationUtil.getType().factory)
            .also {
                val config = it.configuration as NpmScriptRunConfiguration
                config.packageJsonPath = packageJsonPath
                config.scriptName = script
                config.workingDir = File(packageJsonPath).parent ?: (project.basePath ?: "")
                runManager.addConfiguration(it)
            }
        runManager.selectedConfiguration = settings

        val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: return
        ExecutionUtil.runConfiguration(settings, executor)
    }
}
