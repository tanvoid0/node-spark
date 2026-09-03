package com.nodespark.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile

/** Root of the structure tree: the test file itself. */
class NodeTestFileElement(private val psiFile: PsiFile) : StructureViewTreeElement {

    override fun getValue(): Any = psiFile

    override fun getPresentation(): ItemPresentation =
        PresentationData(psiFile.name, null, AllIcons.Nodes.TestSourceFolder, null)

    // Re-parsed on every call: the Structure tool window rebuilds by re-walking the root when the
    // PSI changes, so a cached snapshot would go stale the moment the user types.
    override fun getChildren(): Array<TreeElement> =
        TestOutline.parse(psiFile.text).map { NodeTestElement(it, psiFile) }.toTypedArray()

    override fun navigate(requestFocus: Boolean) = Unit
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false
}

/** One describe/it node. */
class NodeTestElement(
    private val node: OutlineNode,
    private val psiFile: PsiFile,
) : StructureViewTreeElement {

    override fun getValue(): Any = node

    override fun getPresentation(): ItemPresentation =
        PresentationData(node.name, null, iconFor(node), null)

    override fun getChildren(): Array<TreeElement> =
        node.children.map { NodeTestElement(it, psiFile) }.toTypedArray()

    override fun navigate(requestFocus: Boolean) {
        val vf = psiFile.virtualFile ?: return
        OpenFileDescriptor(psiFile.project, vf, node.offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = psiFile.virtualFile != null
    override fun canNavigateToSource(): Boolean = canNavigate()

    private fun iconFor(n: OutlineNode) = when {
        n.modifier == TestModifier.SKIP || n.modifier == TestModifier.TODO -> AllIcons.Nodes.TestIgnored
        n.kind == TestKind.DESCRIBE -> AllIcons.Nodes.TestGroup
        else -> AllIcons.RunConfigurations.TestState.Run
    }
}
