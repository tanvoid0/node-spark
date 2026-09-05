package com.nodespark.sdk

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.nodespark.util.NodePackageManager
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class NodeProjectSdkConfigurable(private val project: Project) : Configurable {

    private val sdkCombo = ComboBox<String>()
    private val pmCombo = ComboBox<String>()

    override fun getDisplayName() = "Node.js SDK"

    override fun createComponent(): JComponent {
        refreshSdkList()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Node.js SDK:"), sdkCombo)
            .addLabeledComponent(JBLabel("Package manager:"), pmCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun refreshSdkList() {
        val model = DefaultComboBoxModel<String>()
        model.addElement(AUTO)
        ProjectJdkTable.getInstance()
            .getSdksOfType(NodeJsSdkType.getInstance())
            .forEach { model.addElement(it.name) }
        sdkCombo.model = model

        val svc = NodeProjectSdkService.getInstance(project)
        sdkCombo.selectedItem = svc.sdkName.ifBlank { AUTO }

        val pmModel = DefaultComboBoxModel<String>()
        pmModel.addElement(AUTO)
        NodePackageManager.values().forEach { pmModel.addElement(it.binName) }
        pmCombo.model = pmModel
        pmCombo.selectedItem = svc.packageManagerName.ifBlank { AUTO }
    }

    override fun isModified(): Boolean {
        val svc = NodeProjectSdkService.getInstance(project)
        return selected(sdkCombo) != svc.sdkName || selected(pmCombo) != svc.packageManagerName
    }

    override fun apply() {
        val svc = NodeProjectSdkService.getInstance(project)
        svc.sdkName = selected(sdkCombo)
        svc.packageManagerName = selected(pmCombo)
    }

    override fun reset() = refreshSdkList()

    /** Combo selection as it is stored: the auto entry is the empty string. */
    private fun selected(combo: ComboBox<String>): String =
        (combo.selectedItem as? String ?: "").let { if (it == AUTO) "" else it }

    private companion object {
        const val AUTO = "<auto-detect>"
    }
}
