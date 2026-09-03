package com.nodespark.noderun

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.nodespark.util.NodeProjectUtil
import com.nodespark.util.NodeTestDetector

class NodeAppRunConfigurationProducer : LazyRunConfigurationProducer<NodeAppRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(NodeAppRunConfigurationType::class.java).factory

    override fun setupConfigurationFromContext(
        configuration: NodeAppRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.isDirectory) return false
        val ext = file.extension?.lowercase() ?: return false
        if (!RUNNABLE.contains(ext)) return false
        if (NodeTestDetector.isTestFile(file)) return false   // test files belong to the Node Test producer

        configuration.scriptPath = file.path
        configuration.workingDir = NodeProjectUtil.projectRootFor(context.project, file.path)
        configuration.name = file.name
        return true
    }

    override fun isConfigurationFromContext(
        configuration: NodeAppRunConfiguration,
        context: ConfigurationContext,
    ): Boolean = configuration.scriptPath == context.location?.virtualFile?.path

    private companion object {
        // Not NodeTestDetector.isJsOrTs — that admits jsx/tsx, which node cannot execute under any flag.
        // .ts variants need --experimental-strip-types (22.6+) or node 23.6+ via the Node options field.
        val RUNNABLE = setOf("js", "mjs", "cjs", "ts", "mts", "cts")
    }
}
