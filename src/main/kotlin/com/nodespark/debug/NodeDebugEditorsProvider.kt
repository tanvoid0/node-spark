package com.nodespark.debug

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

class NodeDebugEditorsProvider : XDebuggerEditorsProvider() {

    override fun getFileType(): FileType = PlainTextFileType.INSTANCE

    @Suppress("DEPRECATION", "UnstableApiUsage")
    override fun createDocument(
        project: Project,
        text: String,
        sourcePosition: com.intellij.xdebugger.XSourcePosition?,
        mode: EvaluationMode,
    ): Document {
        return com.intellij.openapi.editor.EditorFactory.getInstance()
            .createDocument(text)
    }
}
