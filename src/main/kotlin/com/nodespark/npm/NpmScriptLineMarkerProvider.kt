package com.nodespark.npm

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
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

    private fun runScript(element: PsiElement, packageJsonPath: String, script: String) =
        NpmScriptRunner.run(element.project, packageJsonPath, script)
}
