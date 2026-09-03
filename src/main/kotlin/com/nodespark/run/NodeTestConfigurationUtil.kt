package com.nodespark.run

import com.intellij.execution.configurations.ConfigurationTypeUtil

object NodeTestConfigurationUtil {
    fun getType(): NodeTestRunConfigurationType =
        ConfigurationTypeUtil.findConfigurationType(NodeTestRunConfigurationType::class.java)
}
