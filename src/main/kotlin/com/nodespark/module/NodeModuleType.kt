package com.nodespark.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.projectRoots.Sdk
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.sdk.NodeJsSdkType
import javax.swing.Icon

/**
 * Module type for JavaScript / TypeScript sources backed by a Node.js SDK.
 *
 * Registered as `com.intellij.moduleType`, so it shows up in
 * Project Structure -> Modules -> Add -> New Module, and the module's
 * Dependencies tab accepts a Node.js SDK as the module SDK.
 */
class NodeModuleType : ModuleType<NodeModuleBuilder>(ID) {

    companion object {
        const val ID = "NODE_JS_MODULE"

        fun getInstance(): NodeModuleType =
            ModuleTypeManager.getInstance().findByID(ID) as NodeModuleType
    }

    override fun createModuleBuilder(): NodeModuleBuilder = NodeModuleBuilder()

    override fun getName(): String = "Node.js"

    override fun getDescription(): String =
        "JavaScript and TypeScript sources run by a Node.js SDK."

    override fun getNodeIcon(isOpened: Boolean): Icon = NodeSparkIcons.RunTest

    /** Only a Node.js SDK is a meaningful module SDK here — anything else gets flagged. */
    override fun isValidSdk(module: Module, projectSdk: Sdk?): Boolean =
        projectSdk?.sdkType is NodeJsSdkType
}
