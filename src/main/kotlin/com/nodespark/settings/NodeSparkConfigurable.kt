package com.nodespark.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.nodespark.sdk.NodeJsSdkType
import com.nodespark.util.NodeCommandLine
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class NodeSparkConfigurable : Configurable {

    private val nodePathField = TextFieldWithBrowseButton()
    private val npmPathField = TextFieldWithBrowseButton()
    private val envVarsField = JBTextField()
    private val autoDetectBox = JBCheckBox("Auto-detect test runner (Jest / Vitest / Mocha)")
    private val runnerCombo = ComboBox(arrayOf("auto", "jest", "vitest", "mocha"))
    private val lspBox = JBCheckBox(
        "Code completion and imports via typescript-language-server (needs the LSP4IJ plugin)",
    )
    private val detectButton = JButton("Detect")
    private val detectedLabel = JBLabel()

    private var panel: JPanel? = null

    override fun getDisplayName() = "NodeSpark"

    override fun createComponent(): JComponent {
        // A descriptor each: the overload below sets the title on the descriptor it is handed, so a
        // shared one would leave both dialogs with whichever title was applied last.
        nodePathField.addBrowseFolderListener(
            "Node.js Executable", null, null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        )
        npmPathField.addBrowseFolderListener(
            "npm Executable", null, null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        )

        for (field in listOf(nodePathField, npmPathField)) {
            (field.textField as? JBTextField)?.emptyText?.text = "Auto-detected (PATH, then the Node.js SDK)"
        }
        detectedLabel.foreground = UIUtil.getContextHelpForeground()
        detectButton.addActionListener {
            nodePathField.text = detect("node").ifEmpty { nodePathField.text }
            npmPathField.text = detect("npm").ifEmpty { npmPathField.text }
            showDetected()
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Node.js path:"), nodePathField)
            .addLabeledComponent(JBLabel("npm path:"), npmPathField)
            .addComponentToRightColumn(detectButton)
            .addComponentToRightColumn(detectedLabel)
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
        s.nodePath = nodePathField.text.trim()
        s.npmPath = npmPathField.text.trim()
        s.defaultEnvVars = envVarsField.text
        s.autoDetectRunner = autoDetectBox.isSelected
        s.runnerOverride = runnerCombo.selectedItem.toString().let {
            if (it == "auto") "" else it
        }
        s.lspEnabled = lspBox.isSelected
        showDetected()
    }

    override fun reset() {
        val s = NodeSparkSettings.instance
        // The fields show exactly what is stored. Putting a detected path in an empty field would
        // make the page dirty the moment it opens, and clicking OK without touching anything would
        // freeze that path in as an override of the project SDK - the opposite of auto-detection.
        // What was detected goes in the label under them, and the Detect button copies it in.
        nodePathField.text = s.nodePath
        npmPathField.text = s.npmPath
        envVarsField.text = s.defaultEnvVars
        autoDetectBox.isSelected = s.autoDetectRunner
        runnerCombo.selectedItem = s.runnerOverride.ifEmpty { "auto" }
        lspBox.isSelected = s.lspEnabled
        showDetected()
    }

    /** Absolute path of a Node executable: PATH first, then the same places the SDK type probes. */
    private fun detect(name: String): String {
        val onPath = NodeCommandLine.onPath(name)
        if (File(onPath).isAbsolute) return onPath
        val binary = binaryName(name)
        for (home in NodeJsSdkType.getInstance().suggestHomePaths()) {
            val candidate = File(home, binary)
            if (candidate.isFile) return candidate.absolutePath
        }
        return ""
    }

    private fun binaryName(name: String): String = when {
        !SystemInfo.isWindows -> name
        name == "node" -> "node.exe"
        else -> "$name.cmd"
    }

    /**
     * What an empty field resolves to. Existence checks only, no `node --version` — this runs on the
     * EDT every time the page is opened.
     */
    private fun showDetected() {
        val overridden = listOf(nodePathField, npmPathField).any { it.text.isNotBlank() }
        val node = detect("node").ifEmpty { "not found" }
        val npm = detect("npm").ifEmpty { "not found" }
        detectedLabel.text = "<html>Detected: node — $node<br/>npm — $npm" +
            (if (overridden) "<br/>A path above overrides this and the project SDK." else "") +
            "</html>"
    }
}
