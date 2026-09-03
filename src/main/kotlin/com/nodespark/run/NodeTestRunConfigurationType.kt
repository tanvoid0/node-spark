package com.nodespark.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class NodeTestRunConfigurationType : ConfigurationType {

    val factory = NodeTestRunConfigurationFactory(this)

    override fun getDisplayName() = "Node Test"
    override fun getConfigurationTypeDescription() = "Run Node.js tests (Jest / Vitest / Mocha)"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.TestState.Run
    override fun getId() = "NODE_TEST_CONFIGURATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}
