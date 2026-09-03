package com.nodespark.run.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nodespark.run.NodeTestRunConfiguration
import javax.swing.JComponent
import javax.swing.JPanel

class NodeTestRunConfigurationEditor(private val project: Project) :
    SettingsEditor<NodeTestRunConfiguration>() {

    private val testFileField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Test File", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    private val testNameFilterField = JBTextField()
    private val workingDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Working Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val envVarsField = JBTextField()
    private val updateSnapshotsBox = JBCheckBox("Update snapshots (-u)")

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Test file:"), testFileField)
        .addLabeledComponent(JBLabel("Test name filter (regex):"), testNameFilterField)
        .addLabeledComponent(JBLabel("Working directory:"), workingDirField)
        .addLabeledComponent(JBLabel("Extra env vars (KEY=VAL,...):"), envVarsField)
        .addComponent(updateSnapshotsBox)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(config: NodeTestRunConfiguration) {
        testFileField.text = config.testFilePath
        testNameFilterField.text = config.testNameFilter
        workingDirField.text = config.workingDir
        envVarsField.text = config.envVars
        updateSnapshotsBox.isSelected = config.updateSnapshots
    }

    override fun applyEditorTo(config: NodeTestRunConfiguration) {
        config.testFilePath = testFileField.text
        config.testNameFilter = testNameFilterField.text
        config.workingDir = workingDirField.text
        config.envVars = envVarsField.text
        config.updateSnapshots = updateSnapshotsBox.isSelected
    }

    override fun createEditor(): JComponent = panel
}
