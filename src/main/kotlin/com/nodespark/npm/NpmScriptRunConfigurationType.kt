package com.nodespark.npm

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

class NpmScriptRunConfigurationType : ConfigurationType {

    val factory = NpmScriptRunConfigurationFactory(this)

    override fun getDisplayName() = "npm"
    override fun getConfigurationTypeDescription() = "Run a package.json script (npm / yarn / pnpm / bun)"
    override fun getIcon(): Icon = AllIcons.Nodes.Console
    override fun getId() = "NPM_SCRIPT_CONFIGURATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class NpmScriptRunConfigurationFactory(type: NpmScriptRunConfigurationType) : ConfigurationFactory(type) {

    override fun getId() = "NpmScriptRunConfigurationFactory"

    // Required, or the platform builds a plain RunConfigurationOptions and getOptions() throws.
    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        NpmScriptRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NpmScriptRunConfiguration(project, this, "npm script")
}

class NpmScriptRunConfigurationOptions : RunConfigurationOptions()

object NpmScriptConfigurationUtil {
    fun getType(): NpmScriptRunConfigurationType =
        ConfigurationTypeUtil.findConfigurationType(NpmScriptRunConfigurationType::class.java)
}
