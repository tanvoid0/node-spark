package com.nodespark.npm

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class NpmScriptRunConfigurationEditor(private val project: Project) :
    SettingsEditor<NpmScriptRunConfiguration>() {

    private val packageJsonField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select package.json", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    private val scriptField = JBTextField()
    private val argumentsField = JBTextField()
    private val workingDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Working Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val envVarsField = JBTextField()

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("package.json:"), packageJsonField)
        .addLabeledComponent(JBLabel("Script:"), scriptField)
        .addLabeledComponent(JBLabel("Arguments:"), argumentsField)
        .addLabeledComponent(JBLabel("Working directory:"), workingDirField)
        .addLabeledComponent(JBLabel("Extra env vars (KEY=VAL,...):"), envVarsField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(config: NpmScriptRunConfiguration) {
        packageJsonField.text = config.packageJsonPath
        scriptField.text = config.scriptName
        argumentsField.text = config.arguments
        workingDirField.text = config.workingDir
        envVarsField.text = config.envVars
    }

    override fun applyEditorTo(config: NpmScriptRunConfiguration) {
        config.packageJsonPath = packageJsonField.text
        config.scriptName = scriptField.text
        config.arguments = argumentsField.text
        config.workingDir = workingDirField.text
        config.envVars = envVarsField.text
    }

    override fun createEditor(): JComponent = panel
}
