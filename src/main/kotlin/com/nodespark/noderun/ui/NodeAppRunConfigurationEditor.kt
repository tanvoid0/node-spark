package com.nodespark.noderun.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nodespark.noderun.NodeAppRunConfiguration
import javax.swing.JComponent
import javax.swing.JPanel

class NodeAppRunConfigurationEditor(private val project: Project) :
    SettingsEditor<NodeAppRunConfiguration>() {

    private val scriptField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select JavaScript File", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    private val nodeOptionsField = JBTextField()
    private val appArgsField = JBTextField()
    private val workingDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Working Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val envVarsField = JBTextField()

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("JavaScript file:"), scriptField)
        .addLabeledComponent(JBLabel("Node options:"), nodeOptionsField)
        .addLabeledComponent(JBLabel("Application arguments:"), appArgsField)
        .addLabeledComponent(JBLabel("Working directory:"), workingDirField)
        .addLabeledComponent(JBLabel("Extra env vars (KEY=VAL,...):"), envVarsField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(config: NodeAppRunConfiguration) {
        scriptField.text = config.scriptPath
        nodeOptionsField.text = config.nodeOptions
        appArgsField.text = config.applicationArgs
        workingDirField.text = config.workingDir
        envVarsField.text = config.envVars
    }

    override fun applyEditorTo(config: NodeAppRunConfiguration) {
        config.scriptPath = scriptField.text
        config.nodeOptions = nodeOptionsField.text
        config.applicationArgs = appArgsField.text
        config.workingDir = workingDirField.text
        config.envVars = envVarsField.text
    }

    override fun createEditor(): JComponent = panel
}
