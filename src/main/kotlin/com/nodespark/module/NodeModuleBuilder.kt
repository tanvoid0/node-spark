package com.nodespark.module

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.nodespark.sdk.NodeJsSdkType

class NodeModuleBuilder : ModuleBuilder() {

    override fun getModuleType(): ModuleType<*> = NodeModuleType.getInstance()

    /** Restricts the wizard's SDK combo to Node.js SDKs. */
    override fun isSuitableSdkType(sdkType: SdkTypeId): Boolean = sdkType is NodeJsSdkType

    override fun setupRootModel(model: ModifiableRootModel) {
        doAddContentEntry(model)
    }
}
