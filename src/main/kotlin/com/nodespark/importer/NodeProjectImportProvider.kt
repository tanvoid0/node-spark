package com.nodespark.importer

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectImportProvider
import com.nodespark.importer.NodeProjectImportBuilder.Companion.PACKAGE_JSON

/**
 * Registers Node.js alongside Eclipse / Gradle / Maven under
 * "Import module from external model".
 */
class NodeProjectImportProvider : ProjectImportProvider() {

    override fun doGetBuilder(): ProjectImportBuilder<*> = NodeProjectImportBuilder()

    // No extra pages — picking the package.json is the whole wizard.
    override fun createSteps(context: WizardContext): Array<ModuleWizardStep> =
        ModuleWizardStep.EMPTY_ARRAY

    override fun canImportFromFile(file: VirtualFile): Boolean = file.name == PACKAGE_JSON

    /** A directory only qualifies when it actually holds a package.json. */
    override fun canImport(fileOrDirectory: VirtualFile, project: Project?): Boolean =
        if (fileOrDirectory.isDirectory) fileOrDirectory.findChild(PACKAGE_JSON) != null
        else canImportFromFile(fileOrDirectory)

    override fun getFileSample(): String = "<b>package.json</b> (Node.js project)"
}
