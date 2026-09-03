package com.nodespark.sdk

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem

@Service(Service.Level.PROJECT)
@State(name = "NodeProjectSdk", storages = [Storage("nodeSpark.xml")])
class NodeProjectSdkService(private val project: Project) :
    PersistentStateComponent<NodeProjectSdkService.State> {

    data class State(var sdkName: String = "")

    private var state = State()

    override fun getState() = state
    override fun loadState(s: State) { state = s }

    var sdkName: String
        get() = state.sdkName
        set(v) { state.sdkName = v }

    /**
     * Resolves the Node.js SDK to use, most specific first:
     * the SDK of the module owning [contextFile] (Project Structure -> Modules -> Dependencies),
     * then the project-level choice, then the first registered Node.js SDK.
     */
    @JvmOverloads
    fun resolvedSdk(contextFile: String? = null): Sdk? {
        contextFile?.let { moduleSdk(it) }?.let { return it }

        val table = ProjectJdkTable.getInstance()
        if (state.sdkName.isNotBlank()) {
            table.findJdk(state.sdkName, NodeJsSdkType.TYPE_ID)?.let { return it }
        }
        // fall back to first available Node.js SDK
        return table.getSdksOfType(NodeJsSdkType.getInstance()).firstOrNull()
    }

    /** Module SDK for the file, if that module has a Node.js SDK assigned or inherited. */
    private fun moduleSdk(path: String): Sdk? {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return null
        return ReadAction.compute<Sdk?, RuntimeException> {
            if (project.isDisposed) return@compute null
            val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: return@compute null
            ModuleRootManager.getInstance(module).sdk?.takeIf { it.sdkType is NodeJsSdkType }
        }
    }

    /** Returns the node executable path from the resolved SDK, or "node" as fallback */
    @JvmOverloads
    fun nodeExecutable(contextFile: String? = null): String {
        val sdk = resolvedSdk(contextFile) ?: return "node"
        return NodeJsSdkType.getInstance().getNodeExecutable(sdk)
    }

    companion object {
        fun getInstance(project: Project): NodeProjectSdkService =
            project.getService(NodeProjectSdkService::class.java)
    }
}
