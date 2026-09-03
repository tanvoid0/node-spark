package com.nodespark.sdk

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class NodeProjectSdkConfigurable(private val project: Project) : Configurable {

    private val sdkCombo = ComboBox<String>()

    override fun getDisplayName() = "Node.js SDK"

    override fun createComponent(): JComponent {
        refreshSdkList()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Node.js SDK:"), sdkCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun refreshSdkList() {
        val model = DefaultComboBoxModel<String>()
        model.addElement("<auto-detect>")
        ProjectJdkTable.getInstance()
            .getSdksOfType(NodeJsSdkType.getInstance())
            .forEach { model.addElement(it.name) }
        sdkCombo.model = model

        val current = NodeProjectSdkService.getInstance(project).sdkName
        sdkCombo.selectedItem = current.ifBlank { "<auto-detect>" }
    }

    override fun isModified(): Boolean {
        val svc = NodeProjectSdkService.getInstance(project)
        val selected = sdkCombo.selectedItem as? String ?: ""
        return selected != (svc.sdkName.ifBlank { "<auto-detect>" })
    }

    override fun apply() {
        val svc = NodeProjectSdkService.getInstance(project)
        val selected = sdkCombo.selectedItem as? String ?: ""
        svc.sdkName = if (selected == "<auto-detect>") "" else selected
    }

    override fun reset() = refreshSdkList()
}
