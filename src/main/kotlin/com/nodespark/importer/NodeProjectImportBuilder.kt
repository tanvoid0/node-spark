package com.nodespark.importer

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.ProjectImportBuilder
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.module.NodeModuleType
import com.nodespark.sdk.NodeProjectSdkService
import java.io.File
import javax.swing.Icon

/**
 * Turns a `package.json` (or the directory holding one) into a Node.js module.
 *
 * Reached from Project Structure -> Modules -> Add -> Import Module ->
 * "Import module from external model" -> Node.js.
 */
class NodeProjectImportBuilder : ProjectImportBuilder<File>() {

    companion object {
        const val PACKAGE_JSON = "package.json"

        /**
         * Content root for an import of [selected]: the directory itself when a directory
         * was picked, otherwise the directory holding the selected `package.json`.
         * Null when the path yields no usable directory.
         */
        fun contentRootFor(selected: String?): File? {
            val path = selected?.takeIf { it.isNotBlank() } ?: return null
            val file = File(path)
            return when {
                file.isDirectory -> file
                else -> file.parentFile
            }
        }
    }

    // Only the setter is part of the API in every supported build: 262 dropped
    // isOpenProjectSettingsAfter() from the hierarchy, so the flag is stored and not read back.
    private var openSettings = false

    override fun getName(): String = "Node.js"

    override fun getIcon(): Icon = NodeSparkIcons.RunTest

    // No per-element selection step — the whole package.json directory is the module.
    override fun getList(): List<File> = emptyList()
    override fun isMarked(element: File?): Boolean = true
    override fun setList(list: MutableList<File>?) {}

    override fun setOpenProjectSettingsAfter(on: Boolean) { openSettings = on }

    override fun commit(
        project: Project,
        model: ModifiableModuleModel?,
        modulesProvider: ModulesProvider?,
        artifactModel: ModifiableArtifactModel?,
    ): List<Module> {
        val root = contentRootFor(fileToImport) ?: return emptyList()

        // When the wizard hands us a model it commits it itself; a null model is ours to commit.
        val moduleModel = model ?: ModuleManager.getInstance(project).getModifiableModel()
        val imlPath = File(root, "${root.name}.iml").absolutePath
        val module = moduleModel.newModule(imlPath, NodeModuleType.ID)

        val rootModel = ModuleRootManager.getInstance(module).modifiableModel
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root)?.let { dir ->
            val entry = rootModel.addContentEntry(dir)
            // node_modules is huge and never worth indexing
            dir.findChild("node_modules")?.let { entry.addExcludeFolder(it) }
        }
        NodeProjectSdkService.getInstance(project).resolvedSdk()?.let { rootModel.sdk = it }

        WriteAction.runAndWait<RuntimeException> {
            rootModel.commit()
            if (model == null) moduleModel.commit()
        }
        return listOf(module)
    }
}
