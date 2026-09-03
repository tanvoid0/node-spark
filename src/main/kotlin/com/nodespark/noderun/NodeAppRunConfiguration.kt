package com.nodespark.noderun

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.nodespark.noderun.ui.NodeAppRunConfigurationEditor
import org.jdom.Element

class NodeAppRunConfiguration(
    project: Project,
    factory: NodeAppRunConfigurationFactory,
    name: String,
) : RunConfigurationBase<NodeAppRunConfigurationOptions>(project, factory, name) {

    // Persisted fields — must stay non-null; jdom attributes reject nulls.
    var scriptPath: String = ""
    var applicationArgs: String = ""
    var nodeOptions: String = ""      // e.g. --experimental-vm-modules
    var workingDir: String = ""
    var envVars: String = ""          // KEY=VAL pairs, comma-separated

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        NodeAppRunConfigurationEditor(project)

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
        NodeAppRunState(env, this)

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("scriptPath", scriptPath)
        element.setAttribute("applicationArgs", applicationArgs)
        element.setAttribute("nodeOptions", nodeOptions)
        element.setAttribute("workingDir", workingDir)
        element.setAttribute("envVars", envVars)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptPath = element.getAttributeValue("scriptPath") ?: ""
        applicationArgs = element.getAttributeValue("applicationArgs") ?: ""
        nodeOptions = element.getAttributeValue("nodeOptions") ?: ""
        workingDir = element.getAttributeValue("workingDir") ?: ""
        envVars = element.getAttributeValue("envVars") ?: ""
    }

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) throw RuntimeConfigurationError("Script path is required")
    }
}
