package com.nodespark.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.nodespark.util.NodeTestDetector

/**
 * Registered for language "textmate" — the language IC 2024.1 gives .js/.ts/.jsx/.tsx via the
 * bundled TextMate plugin. Returns null for anything that is not a test file so nothing else is
 * shadowed.
 */
class NodeTestStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        val vf = psiFile.virtualFile ?: return null
        if (!NodeTestDetector.isTestFile(vf)) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                StructureViewModelBase(psiFile, editor, NodeTestFileElement(psiFile))

            override fun isRootNodeShown(): Boolean = true
        }
    }
}
