package com.nodespark.actions

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.run.NodeTestConfigurationUtil
import com.nodespark.run.NodeTestRunConfiguration
import com.nodespark.util.NodeTestDetector

class RunTestFileAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isTest = file != null && NodeTestDetector.isTestFile(file)
        e.presentation.isEnabledAndVisible = isTest
        e.presentation.icon = NodeSparkIcons.RunTest
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        val settings = RunManager.getInstance(project)
            .createConfiguration(file.nameWithoutExtension, NodeTestConfigurationUtil.getType().factory)

        val config = settings.configuration as? NodeTestRunConfiguration ?: return
        config.testFilePath = file.path
        config.workingDir = project.basePath ?: ""

        // Temporary, not addConfiguration: a one-shot action run shouldn't leave a permanent entry in Run/Debug.
        RunManager.getInstance(project).setTemporaryConfiguration(settings)

        val executor = ExecutorRegistry.getInstance()
            .getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return
        ExecutionUtil.runConfiguration(settings, executor)
    }
}
