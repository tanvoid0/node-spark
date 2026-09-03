package com.nodespark.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.impl.isTrusted
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeProjectUtil
import com.nodespark.util.NodeTestDetector

/**
 * Runs the project-local eslint over the *unsaved* editor text (via --stdin) and turns its findings
 * into annotations. Silent no-op when eslint is not installed in the project.
 *
 * Registered with `language=""` (Language.ANY's id is the empty string) so it covers .js/.ts files
 * whatever the IDE decides their language is — textmate or plain text in Community.
 */
class EslintExternalAnnotator : ExternalAnnotator<EslintExternalAnnotator.Input, List<EslintMessage>>() {

    class Input(val cmd: GeneralCommandLine, val text: String, val timeoutMs: Int)

    /** Read action on a background thread: snapshot everything the process needs. */
    override fun collectInformation(file: PsiFile): Input? {
        if (!LintSettings.instance.eslintEnabled) return null
        // eslint is an executable out of the project's own node_modules, and it loads the project's
        // eslint.config.js — both are attacker-controlled in a repository the user only cloned. This
        // annotator fires on file open with no user gesture, so it must not run in an untrusted project.
        if (!file.project.isTrusted()) return null
        val vf = file.virtualFile ?: return null
        if (!NodeTestDetector.isJsOrTs(vf)) return null
        val workDir = NodeProjectUtil.projectRootFor(file.project, vf.path)
        val bin = localBin(workDir, "eslint") ?: return null
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return null
        val cmd = NodeCommandLine.base(file.project, workDir)
        cmd.exePath = bin  // the .cmd shim is the executable, never an argument to node
        cmd.addParameters("--format", "json", "--stdin", "--stdin-filename", vf.path)
        return Input(cmd, doc.text, LintSettings.instance.eslintTimeoutMs)
    }

    /** Pooled thread with NO read action: touch nothing but the snapshot. */
    override fun doAnnotate(collectedInfo: Input?): List<EslintMessage> {
        val info = collectedInfo ?: return emptyList()
        return try {
            val handler = CapturingProcessHandler(info.cmd)  // process starts here
            // eslint emits nothing until stdin closes, so writing before runProcess cannot deadlock.
            handler.processInput?.use { it.write(info.text.toByteArray(Charsets.UTF_8)) }
            val out = handler.runProcess(info.timeoutMs, true)
            // eslint exits 1 whenever it finds problems; 2+ means no config / crash.
            if (out.isTimeout || out.exitCode !in 0..1) emptyList()
            else EslintOutputParser.parse(out.stdout)
        } catch (e: Exception) {
            // ponytail: swallow-all, no notification — surfacing config errors needs a notificationGroup
            emptyList()
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<EslintMessage>?, holder: AnnotationHolder) {
        val results = annotationResult ?: return
        if (results.isEmpty()) return
        val vf = file.virtualFile ?: return
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return
        // Recompute against the CURRENT document — it may have moved on since collectInformation.
        val lineStarts = IntArray(doc.lineCount) { doc.getLineStartOffset(it) }
        val length = minOf(doc.textLength, file.textLength)
        for (m in results) {
            val r = EslintOutputParser.toOffsets(m, lineStarts, length) ?: continue
            val severity = if (m.severity >= 2) HighlightSeverity.ERROR else HighlightSeverity.WARNING
            val text = m.ruleId?.let { "${m.message} ($it)" } ?: m.message
            holder.newAnnotation(severity, text).range(TextRange(r.first, r.last)).create()
        }
    }
}
