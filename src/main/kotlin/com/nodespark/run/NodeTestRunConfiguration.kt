package com.nodespark.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.nodespark.run.ui.NodeTestRunConfigurationEditor
import org.jdom.Element

class NodeTestRunConfiguration(
    project: Project,
    factory: NodeTestRunConfigurationFactory,
    name: String,
) : RunConfigurationBase<NodeTestRunConfigurationOptions>(project, factory, name) {

    // Persisted fields
    var testFilePath: String = ""
    var testNameFilter: String = ""   // --testNamePattern / -t
    // The filter names a describe block rather than a single test: jest and mocha match against the
    // full "suite test" name, so a suite filter must not be anchored at the end or it matches nothing.
    var suiteFilter: Boolean = false
    // The describe chain down to and including [testNameFilter], when the configuration came from a
    // gutter icon or the caret. jest and mocha match the whole chain, so knowing it is what stops
    // the suite "login" from also running "auth > login fails". Empty for a hand-typed filter.
    var testNamePath: List<String> = emptyList()
    var workingDir: String = ""
    var envVars: String = ""          // extra KEY=VAL pairs, comma-separated
    var updateSnapshots: Boolean = false   // jest/vitest -u

    override fun getOptions(): NodeTestRunConfigurationOptions =
        super.getOptions() as NodeTestRunConfigurationOptions

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        NodeTestRunConfigurationEditor(project)

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
        NodeTestRunState(env, this)

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("testFilePath", testFilePath)
        element.setAttribute("testNameFilter", testNameFilter)
        element.setAttribute("workingDir", workingDir)
        element.setAttribute("envVars", envVars)
        element.setAttribute("updateSnapshots", updateSnapshots.toString())
        element.setAttribute("suiteFilter", suiteFilter.toString())
        element.removeChildren(TEST_NAME_PATH)
        for (name in testNamePath) {
            element.addContent(Element(TEST_NAME_PATH).setAttribute("name", name))
        }
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        testFilePath = element.getAttributeValue("testFilePath") ?: ""
        testNameFilter = element.getAttributeValue("testNameFilter") ?: ""
        workingDir = element.getAttributeValue("workingDir") ?: ""
        envVars = element.getAttributeValue("envVars") ?: ""
        updateSnapshots = element.getAttributeValue("updateSnapshots")?.toBoolean() ?: false
        suiteFilter = element.getAttributeValue("suiteFilter")?.toBoolean() ?: false
        testNamePath = element.getChildren(TEST_NAME_PATH).mapNotNull { it.getAttributeValue("name") }
    }

    private companion object {
        const val TEST_NAME_PATH = "testNamePathEntry"
    }

    override fun checkConfiguration() {
        if (testFilePath.isBlank()) {
            throw RuntimeConfigurationError("Test file path is required")
        }
    }
}
