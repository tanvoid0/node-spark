package com.nodespark.lint

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import com.nodespark.util.NodeTestDetector

/**
 * Makes Reformat Code (Ctrl+Alt+L) run the project's prettier on .js/.jsx/.ts/.tsx, the way
 * WebStorm's "Run Prettier on Reformat Code action" does.
 *
 * The platform owns the hard parts through this EP: it runs the process off the EDT, keeps the
 * caret and folding, and puts the result in one undoable command. It only asks us whether we can
 * format a file — and we say no unless the setting is on AND the project has its own prettier, so
 * nothing is imposed on a project that has not opted in.
 */
class PrettierFormattingService : AsyncDocumentFormattingService() {

    override fun getName() = "Prettier"

    override fun getNotificationGroupId() = "NodeSpark"

    /** Prettier is whole-file only: no range formatting, no import optimising. */
    override fun getFeatures(): MutableSet<FormattingService.Feature> = mutableSetOf()

    override fun canFormat(file: PsiFile): Boolean {
        if (!LintSettings.instance.prettierFormatter) return false
        val vf = file.virtualFile ?: return false
        return NodeTestDetector.isJsOrTs(vf) && PrettierRunner.isAvailable(file.project, vf)
    }

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val vf = request.context.virtualFile ?: return null
        val cmd = PrettierRunner.command(request.context.project, vf) ?: return null
        val timeout = LintSettings.instance.prettierTimeoutMs
        return object : FormattingTask {
            // The process is started in run(), off the EDT: CapturingProcessHandler's constructor
            // is what spawns it.
            @Volatile private var handler: CapturingProcessHandler? = null

            override fun run() {
                val h = try {
                    CapturingProcessHandler(cmd).also { handler = it }
                } catch (e: Exception) {
                    request.onError("Prettier", "Could not start prettier: ${e.message}")
                    return
                }
                val out = PrettierRunner.format(h, request.documentText, timeout)
                if (out == null) {
                    // Almost always a syntax error in the file being typed; a notification per
                    // keystroke-triggered reformat would be noise, but Ctrl+Alt+L is a gesture.
                    request.onError("Prettier", "prettier could not format this file")
                } else {
                    request.onTextReady(out)
                }
            }

            override fun cancel(): Boolean {
                handler?.destroyProcess()
                return true
            }
        }
    }
}
