package com.nodespark.npm

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class NpmScriptRunConfigurationProducer : LazyRunConfigurationProducer<NpmScriptRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        NpmScriptConfigurationUtil.getType().factory

    override fun setupConfigurationFromContext(
        configuration: NpmScriptRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.name != "package.json") return false
        val script = scriptAt(context) ?: return false

        configuration.packageJsonPath = file.path
        configuration.scriptName = script
        configuration.workingDir = file.parent?.path ?: ""
        configuration.name = script
        return true
    }

    override fun isConfigurationFromContext(
        configuration: NpmScriptRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.packageJsonPath == file.path && configuration.scriptName == scriptAt(context)
    }

    private fun scriptAt(context: ConfigurationContext): String? =
        context.psiLocation?.let { NpmScripts.scriptNameOf(it) }
}
