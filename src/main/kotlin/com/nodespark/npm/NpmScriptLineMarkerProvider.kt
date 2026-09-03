package com.nodespark.npm

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.nodespark.icons.NodeSparkIcons

/** Gutter ▶ on every key of the top-level "scripts" object in package.json. Both mouse buttons run; debug is out of scope. */
class NpmScriptLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null                     // platform requires a leaf anchor
        if (element.containingFile?.name != "package.json") return null  // cheapest guard first (hot path)

        val literal = element.parent as? JsonStringLiteral ?: return null
        if (!literal.isPropertyName) return null
        val prop = literal.parent as? JsonProperty ?: return null
        val script = NpmScripts.scriptNameOfProperty(prop) ?: return null
        val pkgPath = element.containingFile?.virtualFile?.path ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            NodeSparkIcons.RunTest,
            { "Run '$script'" },
            { _, el -> runScript(el, pkgPath, script) },
            GutterIconRenderer.Alignment.LEFT,
            { "Run '$script'" }
        )
    }

    private fun runScript(element: PsiElement, packageJsonPath: String, script: String) {
        val project = element.project
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration(script, NpmScriptConfigurationUtil.getType().factory)
        val config = settings.configuration as NpmScriptRunConfiguration
        config.packageJsonPath = packageJsonPath
        config.scriptName = script
        config.workingDir = java.io.File(packageJsonPath).parent ?: (project.basePath ?: "")
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        val executor = ExecutorRegistry.getInstance()
            .getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return
        ExecutionUtil.runConfiguration(settings, executor)
    }
}
