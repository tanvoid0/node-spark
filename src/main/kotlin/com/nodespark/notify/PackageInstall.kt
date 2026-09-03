package com.nodespark.notify

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.EditorNotifications
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodePackageManager
import java.io.File

/**
 * Runs a package manager in [root] and streams it into a Run tool window tab, refreshing the VFS
 * and the editor banners once it finishes.
 *
 * Shared by every banner that offers to install something, so that the process handling — which is
 * the fiddly part — exists once.
 */
object PackageInstall {

    /**
     * EDT — called from a banner's hyperlink handler, which is what RunContentExecutor.run() requires.
     * [afterCompletion] runs on the process-notifier thread.
     */
    // ponytail: the handler ctor spawns the process on EDT (a few hundred ms for a .cmd shim on
    // Windows); move the spawn to a pooled thread and invokeLater the executor if it ever feels laggy.
    @JvmOverloads
    fun run(
        project: Project,
        root: String,
        pm: NodePackageManager,
        args: List<String>,
        title: String,
        afterCompletion: () -> Unit = {},
    ) {
        val cmd = NodeCommandLine.base(project, root).apply {
            // Absolute, PATH-resolved: the working directory is the project root, and Windows
            // CreateProcess would otherwise prefer an npm.cmd committed to the repository.
            exePath = NodeCommandLine.onPath(pm.binary())
            addParameters(args)
        }
        val handler = try {
            KillableColoredProcessHandler(cmd)
        } catch (e: ExecutionException) {
            Messages.showErrorDialog(project, e.message ?: "Failed to start ${pm.binName}", "NodeSpark")
            return
        }
        ProcessTerminatedListener.attach(handler, project)
        RunContentExecutor(project, handler)
            .withTitle(title)
            .withActivateToolWindow(true)
            .withAfterCompletion { // process-notifier thread
                VfsUtil.markDirtyAndRefresh(true, true, true, File(root))
                afterCompletion()
                ApplicationManager.getApplication().invokeLater {
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
            .run() // starts the process itself — do NOT call handler.startNotify()
    }
}
