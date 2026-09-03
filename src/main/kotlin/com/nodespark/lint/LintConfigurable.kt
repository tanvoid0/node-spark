package com.nodespark.lint

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class LintConfigurable : Configurable {

    private val eslintBox = JBCheckBox("Enable ESLint inspections (project-local eslint only)")
    private val prettierOnSaveBox = JBCheckBox("Run Prettier on save")
    private val prettierFormatterBox =
        JBCheckBox("Use Prettier for Reformat Code (Ctrl+Alt+L) in .js/.jsx/.ts/.tsx")
    private val prettierIssuesBox = JBCheckBox("Highlight code that differs from Prettier's output")
    private val timeoutSpinner = JBIntSpinner(5000, 500, 60000, 500)
    private val prettierTimeoutSpinner = JBIntSpinner(5000, 500, 60000, 500)

    private var panel: JPanel? = null

    override fun getDisplayName() = "ESLint & Prettier"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addComponent(eslintBox)
            .addLabeledComponent(JBLabel("ESLint timeout (ms):"), timeoutSpinner)
            .addComponent(prettierFormatterBox)
            .addComponent(prettierIssuesBox)
            .addComponent(prettierOnSaveBox)
            .addLabeledComponent(JBLabel("Prettier timeout (ms):"), prettierTimeoutSpinner)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = LintSettings.instance
        return eslintBox.isSelected != s.eslintEnabled ||
               prettierOnSaveBox.isSelected != s.prettierOnSave ||
               prettierFormatterBox.isSelected != s.prettierFormatter ||
               prettierIssuesBox.isSelected != s.prettierIssues ||
               timeoutSpinner.number != s.eslintTimeoutMs ||
               prettierTimeoutSpinner.number != s.prettierTimeoutMs
    }

    override fun apply() {
        val s = LintSettings.instance
        s.eslintEnabled = eslintBox.isSelected
        s.prettierOnSave = prettierOnSaveBox.isSelected
        s.prettierFormatter = prettierFormatterBox.isSelected
        s.prettierIssues = prettierIssuesBox.isSelected
        s.eslintTimeoutMs = timeoutSpinner.number
        s.prettierTimeoutMs = prettierTimeoutSpinner.number
    }

    override fun reset() {
        val s = LintSettings.instance
        eslintBox.isSelected = s.eslintEnabled
        prettierOnSaveBox.isSelected = s.prettierOnSave
        prettierFormatterBox.isSelected = s.prettierFormatter
        prettierIssuesBox.isSelected = s.prettierIssues
        timeoutSpinner.number = s.eslintTimeoutMs
        prettierTimeoutSpinner.number = s.prettierTimeoutMs
    }
}
