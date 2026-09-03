package com.nodespark.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class NodeSparkConfigurable : Configurable {

    private val nodePathField = JBTextField()
    private val npmPathField = JBTextField()
    private val envVarsField = JBTextField()
    private val autoDetectBox = JBCheckBox("Auto-detect test runner (Jest / Vitest / Mocha)")
    private val runnerCombo = ComboBox(arrayOf("auto", "jest", "vitest", "mocha"))
    private val lspBox = JBCheckBox(
        "Code completion and imports via typescript-language-server (needs the LSP4IJ plugin)",
    )

    private var panel: JPanel? = null

    override fun getDisplayName() = "NodeSpark"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Node.js path:"), nodePathField)
            .addLabeledComponent(JBLabel("npm path:"), npmPathField)
            .addLabeledComponent(JBLabel("Default env vars (KEY=VAL,...):"), envVarsField)
            .addComponent(autoDetectBox)
            .addLabeledComponent(JBLabel("Runner override:"), runnerCombo)
            .addComponent(lspBox)
            .addComponentToRightColumn(
                JBLabel("Runs the project's own node_modules/typescript-language-server."),
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = NodeSparkSettings.instance
        return nodePathField.text != s.nodePath ||
               npmPathField.text != s.npmPath ||
               envVarsField.text != s.defaultEnvVars ||
               autoDetectBox.isSelected != s.autoDetectRunner ||
               runnerCombo.selectedItem != (s.runnerOverride.ifEmpty { "auto" }) ||
               lspBox.isSelected != s.lspEnabled
    }

    override fun apply() {
        val s = NodeSparkSettings.instance
        s.nodePath = nodePathField.text
        s.npmPath = npmPathField.text
        s.defaultEnvVars = envVarsField.text
        s.autoDetectRunner = autoDetectBox.isSelected
        s.runnerOverride = runnerCombo.selectedItem.toString().let {
            if (it == "auto") "" else it
        }
        s.lspEnabled = lspBox.isSelected
    }

    override fun reset() {
        val s = NodeSparkSettings.instance
        nodePathField.text = s.nodePath
        npmPathField.text = s.npmPath
        envVarsField.text = s.defaultEnvVars
        autoDetectBox.isSelected = s.autoDetectRunner
        runnerCombo.selectedItem = s.runnerOverride.ifEmpty { "auto" }
        lspBox.isSelected = s.lspEnabled
    }
}
