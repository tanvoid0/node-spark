package com.nodespark.npm

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element

class NpmScriptRunConfiguration(
    project: Project,
    factory: NpmScriptRunConfigurationFactory,
    name: String,
) : RunConfigurationBase<NpmScriptRunConfigurationOptions>(project, factory, name) {

    var packageJsonPath: String = ""
    var scriptName: String = ""
    var arguments: String = ""
    var workingDir: String = ""
    var envVars: String = ""   // extra KEY=VAL pairs, comma-separated

    override fun getOptions(): NpmScriptRunConfigurationOptions =
        super.getOptions() as NpmScriptRunConfigurationOptions

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        NpmScriptRunConfigurationEditor(project)

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
        NpmScriptRunState(env, this)

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("packageJsonPath", packageJsonPath)
        element.setAttribute("scriptName", scriptName)
        element.setAttribute("arguments", arguments)
        element.setAttribute("workingDir", workingDir)
        element.setAttribute("envVars", envVars)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        packageJsonPath = element.getAttributeValue("packageJsonPath") ?: ""
        scriptName = element.getAttributeValue("scriptName") ?: ""
        arguments = element.getAttributeValue("arguments") ?: ""
        workingDir = element.getAttributeValue("workingDir") ?: ""
        envVars = element.getAttributeValue("envVars") ?: ""
    }

    override fun checkConfiguration() {
        if (packageJsonPath.isBlank()) throw RuntimeConfigurationError("package.json path is required")
        if (scriptName.isBlank()) throw RuntimeConfigurationError("Script name is required")
    }
}
