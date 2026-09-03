package com.nodespark.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Light platform tests — run inside a sandboxed IntelliJ instance.
 * No real Node.js or filesystem access needed.
 */
class NodeTestRunConfigurationTest : BasePlatformTestCase() {

    private fun makeConfig(name: String = "Test"): NodeTestRunConfiguration {
        val type = NodeTestRunConfigurationType()
        return NodeTestRunConfiguration(project, type.factory, name)
    }

    // ── basic properties ────────────────────────────────────────────────────

    @Test fun `testConfigurationHasCorrectDefaults`() {
        val config = makeConfig()
        assertEquals("", config.testFilePath)
        assertEquals("", config.testNameFilter)
        assertEquals("", config.workingDir)
        assertEquals("", config.envVars)
    }

    @Test fun `testConfigurationStoresTestFilePath`() {
        val config = makeConfig()
        config.testFilePath = "/project/src/math.test.js"
        assertEquals("/project/src/math.test.js", config.testFilePath)
    }

    @Test fun `testConfigurationStoresTestNameFilter`() {
        val config = makeConfig()
        config.testNameFilter = "adds two numbers"
        assertEquals("adds two numbers", config.testNameFilter)
    }

    @Test fun `testConfigurationStoresWorkingDir`() {
        val config = makeConfig()
        config.workingDir = "/project"
        assertEquals("/project", config.workingDir)
    }

    @Test fun `testConfigurationStoresEnvVars`() {
        val config = makeConfig()
        config.envVars = "CI=true,NODE_ENV=test"
        assertEquals("CI=true,NODE_ENV=test", config.envVars)
    }

    // ── checkConfiguration ──────────────────────────────────────────────────

    @Test fun `checkConfigurationThrowsWhenNoFilePath`() {
        val config = makeConfig()
        config.testFilePath = ""
        try {
            config.checkConfiguration()
            fail("Expected RuntimeConfigurationError")
        } catch (e: com.intellij.execution.configurations.RuntimeConfigurationError) {
            assertEquals("Test file path is required", e.message)
        }
    }

    @Test fun `checkConfigurationPassesWhenFilePathSet`() {
        val config = makeConfig()
        config.testFilePath = "/some/file.test.js"
        config.checkConfiguration() // must not throw
    }

    // ── serialization ───────────────────────────────────────────────────────

    @Test fun `testConfigurationRoundTripsViaXml`() {
        val config = makeConfig("RoundTrip")
        config.testFilePath = "/project/foo.test.js"
        config.testNameFilter = "my test"
        config.workingDir = "/project"
        config.envVars = "NODE_ENV=test"

        val element = org.jdom.Element("config")
        config.writeExternal(element)

        val restored = makeConfig("RoundTrip")
        restored.readExternal(element)

        assertEquals(config.testFilePath, restored.testFilePath)
        assertEquals(config.testNameFilter, restored.testNameFilter)
        assertEquals(config.workingDir, restored.workingDir)
        assertEquals(config.envVars, restored.envVars)
    }

    // ── type & factory ──────────────────────────────────────────────────────

    @Test fun `configurationTypeHasCorrectId`() {
        val type = NodeTestRunConfigurationType()
        assertEquals("NODE_TEST_CONFIGURATION", type.id)
    }

    @Test fun `factoryCreatesCorrectConfigurationType`() {
        val type = NodeTestRunConfigurationType()
        val settings = com.intellij.execution.RunManager.getInstance(project)
            .createConfiguration("test", type.factory)
        assertTrue(settings.configuration is NodeTestRunConfiguration)
    }
}
