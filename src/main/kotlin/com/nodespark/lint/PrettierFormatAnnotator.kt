package com.nodespark.lint

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.nodespark.util.NodeTestDetector

/**
 * Highlights the parts of a .js/.jsx/.ts/.tsx file that differ from what the project's prettier
 * would produce, with a "Reformat with Prettier" fix — WebStorm's format-issue highlighting, on
 * top of the project's own prettier and its own config.
 *
 * Advisory only: a weak warning, off unless the setting is on, and silent in any project that has
 * no prettier of its own.
 *
 * Registered with `language=""` (Language.ANY) for the same reason as the ESLint annotator: in
 * Community these files may be textmate or plain text.
 */
class PrettierFormatAnnotator : ExternalAnnotator<PrettierFormatAnnotator.Input, List<Pair<Int, Int>>>() {

    class Input(val cmd: GeneralCommandLine, val text: String, val timeoutMs: Int)

    /** Read action on a background thread: snapshot everything the process needs. */
    override fun collectInformation(file: PsiFile): Input? {
        if (!LintSettings.instance.prettierIssues) return null
        val vf = file.virtualFile ?: return null
        if (!NodeTestDetector.isJsOrTs(vf)) return null
        val cmd = PrettierRunner.command(file.project, vf) ?: return null
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return null
        return Input(cmd, doc.text, LintSettings.instance.prettierTimeoutMs)
    }

    /** Pooled thread with NO read action: touch nothing but the snapshot. */
    override fun doAnnotate(collectedInfo: Input?): List<Pair<Int, Int>> {
        val info = collectedInfo ?: return emptyList()
        // null = prettier crashed, timed out, or the file does not parse while it is being typed.
        // Nothing to say in any of those cases.
        val formatted = PrettierRunner.format(info.cmd, info.text, info.timeoutMs) ?: return emptyList()
        if (formatted == info.text) return emptyList()
        return PrettierDiff.changedLines(info.text, formatted)
    }

    override fun apply(file: PsiFile, annotationResult: List<Pair<Int, Int>>?, holder: AnnotationHolder) {
        val results = annotationResult ?: return
        if (results.isEmpty()) return
        val vf = file.virtualFile ?: return
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return
        val lineStarts = IntArray(doc.lineCount) { doc.getLineStartOffset(it) }
        val length = minOf(doc.textLength, file.textLength)
        val fix = ReformatWithPrettierFix()
        for ((startLine, endLine) in results) {
            val range = PrettierDiff.toRange(startLine, endLine, lineStarts, length) ?: continue
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Reformat with Prettier")
                .range(range)
                .withFix(fix)
                .create()
        }
    }
}

/** Routes through Reformat Code, so the fix and Ctrl+Alt+L are the same code path. */
class ReformatWithPrettierFix : IntentionAction {

    override fun getText() = "Reformat with Prettier"

    override fun getFamilyName() = "NodeSpark"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file != null) CodeStyleManager.getInstance(project).reformat(file)
    }
}
