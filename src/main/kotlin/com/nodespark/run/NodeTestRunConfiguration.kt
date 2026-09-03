package com.nodespark.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.nodespark.run.ui.NodeTestRunConfigurationEditor
import org.jdom.Element

class NodeTestRunConfiguration(
    project: Project,
    factory: NodeTestRunConfigurationFactory,
    name: String,
) : RunConfigurationBase<NodeTestRunConfigurationOptions>(project, factory, name) {

    // Persisted fields
    var testFilePath: String = ""
    var testNameFilter: String = ""   // --testNamePattern / -t
    var workingDir: String = ""
    var envVars: String = ""          // extra KEY=VAL pairs, comma-separated
    var updateSnapshots: Boolean = false   // jest/vitest -u

    override fun getOptions(): NodeTestRunConfigurationOptions =
        super.getOptions() as NodeTestRunConfigurationOptions

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        NodeTestRunConfigurationEditor(project)

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
        NodeTestRunState(env, this)

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("testFilePath", testFilePath)
        element.setAttribute("testNameFilter", testNameFilter)
        element.setAttribute("workingDir", workingDir)
        element.setAttribute("envVars", envVars)
        element.setAttribute("updateSnapshots", updateSnapshots.toString())
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        testFilePath = element.getAttributeValue("testFilePath") ?: ""
        testNameFilter = element.getAttributeValue("testNameFilter") ?: ""
        workingDir = element.getAttributeValue("workingDir") ?: ""
        envVars = element.getAttributeValue("envVars") ?: ""
        updateSnapshots = element.getAttributeValue("updateSnapshots")?.toBoolean() ?: false
    }

    override fun checkConfiguration() {
        if (testFilePath.isBlank()) {
            throw RuntimeConfigurationError("Test file path is required")
        }
    }
}
