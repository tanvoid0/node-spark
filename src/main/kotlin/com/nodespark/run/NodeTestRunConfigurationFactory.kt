package com.nodespark.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project

class NodeTestRunConfigurationFactory(type: NodeTestRunConfigurationType) : ConfigurationFactory(type) {

    override fun getId() = "NodeTestRunConfigurationFactory"

    // Without this the platform builds a plain RunConfigurationOptions and every getOptions() call
    // throws ClassCastException — which the IDE reports on any action update that touches the config.
    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        NodeTestRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NodeTestRunConfiguration(project, this, "Node Test")
}
