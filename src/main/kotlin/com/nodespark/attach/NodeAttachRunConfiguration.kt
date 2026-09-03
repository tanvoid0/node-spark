package com.nodespark.attach

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EmptyRunProfileState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.jdom.Element
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

const val DEFAULT_ATTACH_HOST = "127.0.0.1"
const val DEFAULT_ATTACH_PORT = "9229"

/** Attach the NodeSpark CDP debugger to an already-running `node --inspect` process. */
class NodeAttachRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var host: String = DEFAULT_ATTACH_HOST
    var port: String = DEFAULT_ATTACH_PORT   // String so a typo survives the round-trip to the editor

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = NodeAttachEditor()

    // Must not be null: GenericProgramRunner.execute returns early on a null state, silently.
    // EmptyRunProfileState is never executed — doExecute ignores it.
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        EmptyRunProfileState.INSTANCE

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("host", host)
        element.setAttribute("port", port)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        host = element.getAttributeValue("host") ?: DEFAULT_ATTACH_HOST
        port = element.getAttributeValue("port") ?: DEFAULT_ATTACH_PORT
    }

    override fun checkConfiguration() {
        validate(host, port)?.let { throw RuntimeConfigurationError(it) }
    }

    companion object {
        /** Error message, or null when valid. Pure so it can be unit-tested without a Project. */
        fun validate(host: String, port: String): String? {
            if (host.isBlank()) return "Host is required"
            val p = port.trim().toIntOrNull() ?: return "Port must be a number"
            if (p !in 1..65535) return "Port must be between 1 and 65535"
            return null
        }
    }
}

class NodeAttachEditor : SettingsEditor<NodeAttachRunConfiguration>() {

    private val hostField = JBTextField()
    private val portField = JBTextField()

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Host:"), hostField)
        .addLabeledComponent(JBLabel("Debug port:"), portField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(config: NodeAttachRunConfiguration) {
        hostField.text = config.host
        portField.text = config.port
    }

    override fun applyEditorTo(config: NodeAttachRunConfiguration) {
        config.host = hostField.text
        config.port = portField.text
    }

    override fun createEditor(): JComponent = panel
}

class NodeAttachConfigurationFactory(type: NodeAttachConfigurationType) : ConfigurationFactory(type) {

    override fun getId() = "NodeAttachConfigurationFactory"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NodeAttachRunConfiguration(project, this, "Attach to Node.js")
}

class NodeAttachConfigurationType : ConfigurationType {

    private val factory = NodeAttachConfigurationFactory(this)

    override fun getDisplayName() = "Attach to Node.js"
    override fun getConfigurationTypeDescription() = "Attach to a running node --inspect process"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.RemoteDebug
    override fun getId() = "NODE_ATTACH_CONFIGURATION"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}
