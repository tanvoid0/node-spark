package com.nodespark.module

import com.intellij.openapi.projectRoots.impl.UnknownSdkType
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nodespark.sdk.NodeJsSdkType

class NodeModuleTypeTest : BasePlatformTestCase() {

    private val moduleType = NodeModuleType()

    fun `test_registered under the id used in plugin xml`() {
        assertEquals("NODE_JS_MODULE", moduleType.id)
        assertEquals("NODE_JS_MODULE", NodeModuleType.ID)
    }

    fun `test_builder reports this module type`() {
        assertTrue(moduleType.createModuleBuilder() is NodeModuleBuilder)
    }

    fun `test_only node sdks are suitable for the wizard`() {
        val builder = NodeModuleBuilder()
        assertTrue(builder.isSuitableSdkType(NodeJsSdkType()))
        assertFalse(builder.isSuitableSdkType(UnknownSdkType.getInstance("Foo")))
    }

    fun `test_node sdk is a valid module sdk and others are not`() {
        val nodeSdk = ProjectJdkImpl("Node.js v20", NodeJsSdkType(), "/tmp/node", "v20.0.0")
        assertTrue(moduleType.isValidSdk(module, nodeSdk))
        assertFalse(moduleType.isValidSdk(module, null))
    }

    fun `test_module type is registered from plugin xml`() {
        val registered = ModuleTypeManager.getInstance().findByID(NodeModuleType.ID)
        assertTrue("NODE_JS_MODULE not registered — check the <moduleType> entry in plugin.xml",
            registered is NodeModuleType)
    }

    fun `test_new module wizard offers the node builder`() {
        val builders = ModuleBuilder.getAllBuilders()
        assertTrue("Node.js builder missing from the New Module wizard list",
            builders.any { it is NodeModuleBuilder })
    }
}
