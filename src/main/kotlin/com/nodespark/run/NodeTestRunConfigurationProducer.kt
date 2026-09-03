package com.nodespark.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
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
        configuration.name = file.nameWithoutExtension

        // Check if cursor is inside a specific test block
        val psiFile = context.location?.psiElement?.containingFile
        val editor = com.intellij.openapi.editor.ex.EditorEx::class.java.let {
            com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(context.dataContext)
        }
        if (psiFile != null && editor != null) {
            val offset = editor.caretModel.offset
            val testName = findTestNameAtOffset(psiFile.text, offset)
            if (testName != null) {
                configuration.testNameFilter = testName
                configuration.name = testName
            }
        }

        return true
    }

    override fun isConfigurationFromContext(
        configuration: NodeTestRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.testFilePath == file.path
    }

    private fun findTestNameAtOffset(text: String, offset: Int): String? {
        val matcher = NodeTestDetector.TEST_FUNCTION_PATTERN.matcher(text)
        var lastMatch: String? = null
        var lastStart = -1

        while (matcher.find()) {
            if (matcher.start() <= offset) {
                lastMatch = matcher.group(5) // captured test name
                lastStart = matcher.start()
            } else {
                break
            }
        }
        return lastMatch
    }
}
