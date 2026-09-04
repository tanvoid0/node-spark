package com.nodespark.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.nodespark.structure.TestKind
import com.nodespark.structure.TestOutline
import com.nodespark.util.NodeTestDetector

class NodeTestRunConfigurationProducer : LazyRunConfigurationProducer<NodeTestRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        NodeTestConfigurationUtil.getType().factory

    override fun setupConfigurationFromContext(
        configuration: NodeTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!NodeTestDetector.isTestFile(file)) return false

        val project = context.project
        configuration.testFilePath = file.path
        configuration.workingDir = project.basePath ?: ""
        configuration.name = file.name

        // Name the configuration after the whole describe chain the caret sits in — "UserService >
        // login > returns a token" rather than the bare leaf, which is often just "works".
        val editor = CommonDataKeys.EDITOR.getData(context.dataContext)
        if (editor != null) {
            val path = TestOutline.pathAt(editor.document.immutableCharSequence, editor.caretModel.offset)
            val leaf = path.lastOrNull()
            if (leaf != null) {
                configuration.testNameFilter = leaf.name
                configuration.suiteFilter = leaf.kind == TestKind.DESCRIBE
                configuration.testNamePath = path.map { it.name }
                configuration.name = path.joinToString(" > ") { it.name }
            }
        }

        return true
    }

    override fun isConfigurationFromContext(
        configuration: NodeTestRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (configuration.testFilePath != file.path) return false
        val editor = CommonDataKeys.EDITOR.getData(context.dataContext) ?: return configuration.testNameFilter.isEmpty()
        // The whole chain, not just the leaf: `describe('add', () => it('add', ...))` has the same
        // leaf name at two levels, and reusing the suite's configuration for the test would run it
        // unanchored.
        val path = TestOutline.pathAt(editor.document.immutableCharSequence, editor.caretModel.offset)
        return configuration.testNamePath == path.map { it.name }
    }
}
