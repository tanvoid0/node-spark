package com.nodespark.env

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/** Shared plumbing for the "copy the template into a real .env" flows. */
object EnvTemplates {

    /** Keys of [template] missing from [target], or an empty list if either cannot be read. */
    fun missing(template: VirtualFile, target: VirtualFile): List<EnvFile.Pair> {
        val templateText = text(template) ?: return emptyList()
        val targetText = text(target) ?: return emptyList()
        return EnvSiblings.missingKeys(templateText, targetText)
    }

    fun text(file: VirtualFile): String? =
        FileDocumentManager.getInstance().getDocument(file)?.text
            ?: runCatching { VfsUtil.loadText(file) }.getOrNull()

    /** Appends the keys of [template] that [target] does not have. Returns how many were added. */
    fun fill(project: Project, template: VirtualFile, target: VirtualFile): Int {
        val templateText = text(template) ?: return 0
        val document = FileDocumentManager.getInstance().getDocument(target) ?: return 0
        val updated = EnvSiblings.fillFromTemplate(templateText, document.text, "Added from ${template.name}")
        if (updated == document.text) return 0
        val added = EnvSiblings.missingKeys(templateText, document.text).size
        WriteCommandAction.runWriteCommandAction(project, "Fill from ${template.name}", null, {
            document.setText(updated)
        })
        return added
    }
}

/**
 * Project view: "New Env File from Template" on a .env.example. Prompts for the name, copies the
 * template verbatim (keys, comments and placeholder values) and opens it.
 */
class CreateEnvFromTemplateAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && !file.isDirectory && EnvFileType.matches(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val template = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val directory = template.parent ?: return
        val name = Messages.showInputDialog(
            project,
            "Name of the new env file:",
            "New Env File from ${template.name}",
            null,
            ".env.local",
            object : InputValidator {
                override fun checkInput(input: String) = input.isNotBlank() && EnvFileType.matches(input.trim())
                override fun canClose(input: String) = checkInput(input)
            },
        )?.trim() ?: return

        val existing = directory.findChild(name)
        if (existing != null) {
            val fill = Messages.showYesNoDialog(
                project,
                "$name already exists. Add the keys it is missing from ${template.name}?",
                "File Exists",
                Messages.getQuestionIcon(),
            )
            if (fill == Messages.YES) {
                val added = EnvTemplates.fill(project, template, existing)
                notifyFilled(project, existing, added)
            }
            open(project, existing)
            return
        }

        val text = EnvTemplates.text(template) ?: return
        val created = WriteAction.compute<VirtualFile?, Exception> {
            directory.createChildData(this, name).also { VfsUtil.saveText(it, text) }
        } ?: return
        open(project, created)
    }

    private fun open(project: Project, file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun notifyFilled(project: Project, file: VirtualFile, added: Int) {
        if (added == 0) {
            Messages.showInfoMessage(project, "${file.name} already has every key.", "Nothing to Add")
        }
    }
}

/** Editor banner offering to copy the keys a .env file is missing from its .env.example. */
class EnvTemplateNotificationProvider : EditorNotificationProvider, DumbAware {

    private val dismissed = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!EnvFileType.matches(file) || EnvSiblings.isTemplate(file.name)) return null
        if (file.path in dismissed) return null
        val template = EnvSiblings.template(file) ?: return null
        val missing = EnvTemplates.missing(template, file)
        if (missing.isEmpty()) return null

        val summary = missing.take(3).joinToString(", ") { it.key } +
            if (missing.size > 3) " and ${missing.size - 3} more" else ""
        return Function { _ ->
            EditorNotificationPanel().apply {
                text("${missing.size} key(s) from ${template.name} are missing here: $summary")
                createActionLabel("Add them") {
                    EnvTemplates.fill(project, template, file)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
                createActionLabel("Dismiss") {
                    dismissed.add(file.path)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}
