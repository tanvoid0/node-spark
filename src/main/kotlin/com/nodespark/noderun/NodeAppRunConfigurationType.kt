package com.nodespark.noderun

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

class NodeAppRunConfigurationType : ConfigurationType {

    val factory = NodeAppRunConfigurationFactory(this)

    override fun getId() = "NODE_APP_CONFIGURATION"
    override fun getDisplayName() = "Node.js"
    override fun getConfigurationTypeDescription() = "Run or debug a Node.js script"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.Application
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class NodeAppRunConfigurationFactory(type: NodeAppRunConfigurationType) : ConfigurationFactory(type) {

    override fun getId() = "NodeAppRunConfigurationFactory"

    // Required, or the platform builds a plain RunConfigurationOptions and getOptions() throws.
    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        NodeAppRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NodeAppRunConfiguration(project, this, "Node.js")
}

class NodeAppRunConfigurationOptions : RunConfigurationOptions()
