package com.nodespark.lsp

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.nodespark.notify.NodeModules
import com.nodespark.notify.PackageInstall
import com.nodespark.settings.NodeSparkSettings
import com.nodespark.util.NodePackageManager
import com.nodespark.util.NodeProjectUtil
import com.nodespark.util.NodeTestDetector
import com.redhat.devtools.lsp4ij.LanguageServerManager
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.JComponent

/**
 * Banner offering to install the language server, shown only when completion has been switched on
 * but the project does not have the server that would provide it — otherwise the setting silently
 * does nothing and looks broken.
 *
 * Registered from node-spark-lsp.xml, so it exists only when LSP4IJ does.
 */
class TsServerNotificationProvider : EditorNotificationProvider, DumbAware {

    private val dismissed: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!NodeSparkSettings.instance.lspEnabled) return null
        if (!NodeTestDetector.isJsOrTs(file)) return null
        // Installing runs the dependency tree's postinstall scripts, same as the node_modules banner.
        if (!project.isTrusted()) return null
        // Dependencies are not installed at all yet: that banner is the one to show, not this one.
        if (NodeModules.missingModulesRoot(file.path) != null) return null
        if (TsServer.entry(project) != null) return null

        val root = NodeProjectUtil.projectRootFor(project, file.path)
        if (root in dismissed) return null
        val pm = NodePackageManager.detect(root)
        return Function<FileEditor, JComponent> { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                text = "Code completion needs typescript-language-server in this project"
                createActionLabel("Install as a dev dependency") { install(project, root, pm) }
                createActionLabel("Dismiss") {
                    dismissed.add(root)
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

    /**
     * EDT — called from the hyperlink handler.
     * `typescript` comes along because the server resolves tsserver out of the project, and starting
     * it explicitly afterwards saves the user a restart: nothing else re-triggers a server that
     * failed to start when the binary was missing.
     */
    private fun install(project: Project, root: String, pm: NodePackageManager) =
        PackageInstall.run(
            project,
            root,
            pm,
            pm.addDevArgs(listOf("typescript", TsServer.PACKAGE)),
            "${pm.binName} add ${TsServer.PACKAGE}",
        ) {
            TsServer.invalidate()
            LanguageServerManager.getInstance(project).start(TsServer.SERVER_ID)
        }
}
