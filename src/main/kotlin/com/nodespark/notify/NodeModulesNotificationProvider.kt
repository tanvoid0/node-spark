package com.nodespark.notify

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodePackageManager
import com.nodespark.util.NodeTestDetector
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.JComponent

/** Pure decision logic for the "node modules are not installed" banner. */
object NodeModules {

    /**
     * Nearest ancestor of [path] that owns a package.json with no installed node_modules, or null.
     * Returns null when any ancestor package.json dir *does* have node_modules (deps hoisted in a
     * monorepo), and for anything living inside a node_modules tree.
     */
    fun missingModulesRoot(path: String): String? {
        var dir: File? = File(path).let { if (it.isDirectory) it else it.parentFile }
        var nearest: String? = null
        while (dir != null) {
            if (dir.name == "node_modules") return null
            if (File(dir, "package.json").isFile) {
                if (File(dir, "node_modules").isDirectory) return null
                if (nearest == null) nearest = dir.absolutePath
            }
            dir = dir.parentFile
        }
        return nearest
    }
}
// ponytail: java.io stat walk rather than VFS — the banner is refreshed explicitly after an install;
// switch to VirtualFile.parent walking only if a stale-banner report ever shows up.

/**
 * Banner offering to install dependencies when a JS/TS file (or package.json) sits under a
 * package.json with no node_modules. One provider instance per project (area="IDEA_PROJECT"),
 * so [dismissed] is already project-scoped and dies with the session.
 */
class NodeModulesNotificationProvider : EditorNotificationProvider, DumbAware {

    private val dismissed: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!NodeTestDetector.isJsOrTs(file) && file.name != "package.json") return null
        // `npm install` runs the dependency tree's postinstall scripts, so do not even offer it for a
        // project the user has not trusted.
        if (!project.isTrusted()) return null
        val root = NodeModules.missingModulesRoot(file.path) ?: return null
        if (root in dismissed) return null
        val pm = NodePackageManager.detect(root)
        return Function<FileEditor, JComponent> { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
                text = "Node modules are not installed"
                createActionLabel("Run ${pm.binName} install") { install(project, root, pm) }
                createActionLabel("Dismiss") {
                    dismissed.add(root)
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

    /** EDT — called from the hyperlink handler, which is what RunContentExecutor.run() requires. */
    // ponytail: the handler ctor spawns the process on EDT (a few hundred ms for a .cmd shim on
    // Windows); move the spawn to a pooled thread and invokeLater the executor if it ever feels laggy.
    private fun install(project: Project, root: String, pm: NodePackageManager) {
        val cmd = NodeCommandLine.base(project, root).apply {
            // Absolute, PATH-resolved: the working directory is the project root, and Windows
            // CreateProcess would otherwise prefer an npm.cmd committed to the repository.
            exePath = NodeCommandLine.onPath(pm.binary())
            addParameters(pm.installArgs())
        }
        val handler = try {
            KillableColoredProcessHandler(cmd)
        } catch (e: ExecutionException) {
            Messages.showErrorDialog(project, e.message ?: "Failed to start ${pm.binName}", "NodeSpark")
            return
        }
        ProcessTerminatedListener.attach(handler, project)
        RunContentExecutor(project, handler)
            .withTitle("${pm.binName} install")
            .withActivateToolWindow(true)
            .withAfterCompletion { // process-notifier thread
                VfsUtil.markDirtyAndRefresh(true, true, true, File(root))
                ApplicationManager.getApplication().invokeLater {
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
            .run() // starts the process itself — do NOT call handler.startNotify()
    }
}
