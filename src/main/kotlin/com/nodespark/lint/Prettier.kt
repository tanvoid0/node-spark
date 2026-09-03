package com.nodespark.lint

import com.intellij.execution.util.ExecUtil
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeProjectUtil
import com.nodespark.util.NodeTestDetector

/** `prettier --write <file>` on a pooled thread, then an async VFS refresh so the editor reloads. */
private fun runPrettier(project: Project, file: VirtualFile) {
    // Same reasoning as EslintExternalAnnotator: prettier comes from the project's node_modules and
    // loads the project's config. beforeDocumentSaving reaches here without a user gesture.
    if (!project.isTrusted()) return
    val workDir = NodeProjectUtil.projectRootFor(project, file.path)
    val bin = localBin(workDir, "prettier") ?: return
    val cmd = NodeCommandLine.base(project, workDir)
    cmd.exePath = bin
    cmd.addParameters("--write", file.path)
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
        // prettier missing, crashed or timed out -> leave the file alone
        val ran = try {
            ExecUtil.execAndGetOutput(cmd, 10_000)
            true
        } catch (e: Exception) {
            false
        }
        if (ran) VfsUtil.markDirtyAndRefresh(true, false, false, file)
    })
}

class PrettierFormatAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        e.presentation.isEnabledAndVisible = file != null && project != null &&
            NodeTestDetector.isJsOrTs(file) &&
            localBin(NodeProjectUtil.projectRootFor(project, file.path), "prettier") != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return
        // --write reads from disk, so flush the editor buffer first (we are on the EDT here).
        FileDocumentManager.getInstance().getDocument(file)?.let {
            FileDocumentManager.getInstance().saveDocument(it)
        }
        runPrettier(project, file)
    }
}

/** Registered unconditionally; returns immediately unless the (default-off) setting is on. */
class PrettierOnSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        if (!LintSettings.instance.prettierOnSave) return
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!NodeTestDetector.isJsOrTs(file)) return
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
        // ponytail: invokeLater so prettier runs AFTER this save writes the file instead of racing it;
        //           upgrade path is the com.intellij.actionOnSave EP (ActionsOnSaveFileDocumentManagerListener).
        ApplicationManager.getApplication().invokeLater(Runnable { runPrettier(project, file) })
    }
}
