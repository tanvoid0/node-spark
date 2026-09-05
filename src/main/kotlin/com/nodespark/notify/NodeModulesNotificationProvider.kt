package com.nodespark.notify

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.openapi.ui.Messages
import com.nodespark.npm.LockfileSync
import com.nodespark.npm.LockfileSyncService
import com.nodespark.sdk.NodeProjectSdkService
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

    /**
     * Nearest ancestor of [path] owning a package.json, or null for anything inside node_modules —
     * an installed dependency has a package.json of its own, and it is not the project's.
     */
    fun packageRoot(path: String): String? {
        var dir: File? = File(path).let { if (it.isDirectory) it else it.parentFile }
        var nearest: String? = null
        while (dir != null) {
            if (dir.name == "node_modules") return null
            if (nearest == null && File(dir, "package.json").isFile) nearest = dir.absolutePath
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
        NodeModules.missingModulesRoot(file.path)?.let { root ->
            if ("$root|missing" in dismissed) return null
            val pm = NodeProjectSdkService.getInstance(project).packageManagerFor(root)
            return banner(project, root, "missing", "Node modules are not installed") {
                createActionLabel("Run ${pm.binName} install") {
                    PackageInstall.run(project, root, pm, pm.installArgs(), "${pm.binName} install", refresh(project, root))
                }
            }
        }
        return outOfSync(project, file)
    }

    /**
     * Banner for a lockfile that has drifted from package.json, or a node_modules that has drifted
     * from the lockfile — the state a `git pull` leaves behind, which nothing otherwise reports
     * until something fails at runtime.
     */
    private fun outOfSync(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val root = NodeModules.packageRoot(file.path) ?: return null
        if ("$root|sync" in dismissed) return null
        val report = LockfileSyncService.getInstance(project).report(root)
        if (!report.needsInstall && !report.needsFrozen) return null
        // "No lockfile at all" is a project's own choice, not drift; only flag a lockfile that exists.
        if (report.lockfile == null) return null
        val pm = NodeProjectSdkService.getInstance(project).packageManagerFor(root)
        val args = if (report.needsFrozen) pm.frozenInstallArgs(report.berry) else pm.installArgs()
        val label = (listOf(pm.binName) + args).joinToString(" ")
        return banner(project, root, "sync", report.summary()) {
            createActionLabel("Run $label") {
                PackageInstall.run(project, root, pm, args, label, refresh(project, root))
            }
            createActionLabel("Details") { showDetails(project, report) }
        }
    }

    private fun showDetails(project: Project, report: LockfileSync.Report) {
        val lines = report.drifts.take(DETAIL_LIMIT).joinToString("\n") { "  $it" }
        val more = (report.drifts.size - DETAIL_LIMIT).takeIf { it > 0 }?.let { "\n  … and $it more" }.orEmpty()
        Messages.showInfoMessage(project, "${report.summary()}:\n\n$lines$more", "Dependencies Out Of Sync")
    }

    private fun banner(
        project: Project,
        root: String,
        kind: String,
        message: String,
        actions: EditorNotificationPanel.() -> Unit,
    ) = Function<FileEditor, JComponent> { _ ->
        EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
            text = message
            actions()
            createActionLabel("Dismiss") {
                dismissed.add("$root|$kind")
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }

    /** Runs on the process-notifier thread once an install finishes. */
    private fun refresh(project: Project, root: String): () -> Unit = {
        LockfileSyncService.getInstance(project).invalidate(root)
    }

    private companion object {
        const val DETAIL_LIMIT = 30
    }
}
