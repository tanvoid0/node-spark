package com.nodespark.env

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractAction
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/** Adds a "Variables" tab beside the text editor of any .env file. */
class EnvTableEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = EnvFileType.matches(file)
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = EnvTableEditor(project, file)
    override fun getEditorTypeId(): String = "nodespark-env-table"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

/**
 * Key/value grid over a .env file. The document stays the single source of truth: every edit is
 * written straight back through it, so undo, VCS and the text tab all keep working, and comments
 * and ordering in the file are preserved (see [EnvFile.withPairs]).
 */
class EnvTableEditor(private val project: Project, private val file: VirtualFile) :
    com.intellij.openapi.util.UserDataHolderBase(), FileEditor {

    /**
     * [source] is the pair this row was parsed from. A row that still matches it is written back as
     * the untouched original text, so editing one variable never reformats the rest of the file.
     */
    private data class Row(
        var key: String,
        var value: String,
        var exported: Boolean,
        /** Trailing note on the assignment line. */
        var comment: String,
        /** The `#` block above the assignment, one entry per line. */
        var doc: String,
        val source: EnvFile.Pair? = null,
    ) {
        /** Both kinds of comment as one editable blob: the block first, the inline note last. */
        fun commentCell(): String = listOf(doc, comment).filter { it.isNotEmpty() }.joinToString("\n")

        /**
         * One line stays an inline note when the row had no block to begin with; anything longer
         * becomes the block above, which is the only way a .env file holds more than one line.
         */
        fun setCommentCell(text: String) {
            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            when {
                lines.isEmpty() -> { doc = ""; comment = "" }
                lines.size == 1 && doc.isEmpty() -> { doc = ""; comment = lines[0] }
                else -> { doc = lines.joinToString("\n"); comment = "" }
            }
        }

        fun toPair(): EnvFile.Pair {
            val edited = EnvFile.Pair(key, value, exported, comment, doc)
            // copy() keeps the source's exact text; only a real change is re-rendered.
            return if (source != null && sameContentAs(source)) source else edited
        }

        private fun sameContentAs(other: EnvFile.Pair) = key == other.key &&
            value == other.value && exported == other.exported &&
            comment == other.comment && doc == other.doc
    }

    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private val rows = ArrayList<Row>()
    private var syncing = false
    private var valuesHidden = true

    private val model = object : AbstractTableModel() {
        override fun getRowCount() = rows.size
        override fun getColumnCount() = 3
        override fun getColumnName(column: Int) = when (column) {
            0 -> "Key"
            1 -> "Value"
            else -> "Comment"
        }

        override fun isCellEditable(row: Int, column: Int) = true

        override fun getValueAt(row: Int, column: Int): Any = when (column) {
            0 -> rows[row].key
            1 -> rows[row].value
            else -> rows[row].commentCell()
        }

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            val text = value?.toString().orEmpty()
            when (column) {
                0 -> rows[row].key = text.trim()
                1 -> rows[row].value = text
                // A comment cell holds the note itself; the hashes are added on the way out.
                else -> rows[row].setCommentCell(text)
            }
            push()
        }
    }

    private val table = JBTable(model).apply {
        setShowGrid(true)
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        putClientProperty("terminateEditOnFocusLost", true)
        columnModel.getColumn(1).cellRenderer = MaskingRenderer()
        columnModel.getColumn(2).cellRenderer = CommentRenderer()
        // A .env comment can run to several lines; the expandable field opens a popup (the icon at
        // its right, or Shift+Enter) where they can be edited as lines rather than one long string.
        columnModel.getColumn(2).cellEditor = DefaultCellEditor(
            ExpandableTextField({ it.split("\n") }, { it.joinToString("\n") }),
        )
    }

    private val component: JComponent = JPanel(BorderLayout()).apply {
        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                stopEditing()
                rows.add(Row("", "", false, "", ""))
                model.fireTableDataChanged()
                push()
                val last = rows.size - 1
                table.setRowSelectionInterval(last, last)
                table.editCellAt(last, 0)
                table.editorComponent?.requestFocusInWindow()
            }
            .setRemoveAction {
                stopEditing()
                table.selectedRows.sortedDescending().forEach { rows.removeAt(it) }
                model.fireTableDataChanged()
                push()
            }
            .setMoveUpAction { move(-1) }
            .setMoveDownAction { move(1) }
            .createPanel()
        add(header(), BorderLayout.NORTH)
        add(decorated, BorderLayout.CENTER)
    }

    init {
        installClipboardActions()
        pull()
        document?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (!syncing) pull()
                }
            },
            this,
        )
    }

    private fun header(): JComponent = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(4, 6)
        val hide = JBCheckBox("Hide values", valuesHidden)
        hide.addActionListener {
            valuesHidden = hide.isSelected
            table.repaint()
        }
        add(hide, BorderLayout.WEST)
    }

    private fun move(delta: Int) {
        stopEditing()
        val selected = table.selectedRows.toList().let { if (delta < 0) it else it.reversed() }
        if (selected.any { it + delta !in rows.indices }) return
        for (index in selected) {
            val row = rows.removeAt(index)
            rows.add(index + delta, row)
        }
        model.fireTableDataChanged()
        selected.forEach { table.addRowSelectionInterval(it + delta, it + delta) }
        push()
    }

    /** Document -> table. */
    private fun pull() {
        val text = document?.text ?: return
        rows.clear()
        EnvFile.parse(text).pairs.forEach { rows.add(Row(it.key, it.value, it.exported, it.comment, it.doc, it)) }
        model.fireTableDataChanged()
    }

    /** Table -> document. Rows with an empty key are held in the grid but not written out. */
    private fun push() {
        val doc = document ?: return
        val pairs = rows.filter { it.key.isNotEmpty() }.map { it.toPair() }
        val updated = EnvFile.withPairs(doc.text, pairs)
        if (updated == doc.text) return
        syncing = true
        try {
            WriteCommandAction.runWriteCommandAction(project, "Edit Environment Variables", null, {
                doc.setText(updated)
            })
        } finally {
            syncing = false
        }
    }

    private fun stopEditing() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
    }

    /**
     * Pasting a block of `KEY=value` lines — the usual way a set of variables arrives, out of a
     * password manager or another project — adds one row per line instead of dropping the whole
     * blob into a single cell. Copy goes back out in the same shape.
     */
    private fun installClipboardActions() {
        val input = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val menuMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, menuMask), "nodespark-paste")
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, menuMask), "nodespark-copy")
        table.actionMap.put(
            "nodespark-paste",
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = pasteFromClipboard()
            },
        )
        table.actionMap.put(
            "nodespark-copy",
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = copyToClipboard()
            },
        )
    }

    private fun pasteFromClipboard() {
        val text = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ?: return
        val pasted = EnvFile.parse(text).pairs
        if (pasted.isEmpty()) return
        stopEditing()
        val byKey = rows.associateBy { it.key }
        for (pair in pasted) {
            val existing = byKey[pair.key]
            if (existing != null) {
                existing.value = pair.value
                if (pair.comment.isNotEmpty()) existing.comment = pair.comment
                if (pair.doc.isNotEmpty()) existing.doc = pair.doc
            } else {
                rows.add(Row(pair.key, pair.value, pair.exported, pair.comment, pair.doc))
            }
        }
        model.fireTableDataChanged()
        push()
    }

    private fun copyToClipboard() {
        val selected = table.selectedRows
        val chosen = if (selected.isEmpty()) rows else selected.map { rows[it] }
        val text = chosen.joinToString("\n") { EnvFile.render(it.key, it.value, it.exported, it.comment, it.doc) }
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    /** A multi-line comment has to fit one row, so it is flattened, with the whole text on hover. */
    private class CommentRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            selected: Boolean,
            focused: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val text = value?.toString().orEmpty()
            val component = super.getTableCellRendererComponent(
                table, text.replace("\n", " · "), selected, focused, row, column,
            )
            toolTipText = if ("\n" in text) {
                text.split("\n").joinToString("<br>") { StringUtil.escapeXmlEntities(it) }.let { "<html>$it</html>" }
            } else {
                null
            }
            return component
        }
    }

    private inner class MaskingRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            selected: Boolean,
            focused: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val shown = if (valuesHidden && !value?.toString().isNullOrEmpty()) {
                "•".repeat(value.toString().length.coerceAtMost(24))
            } else {
                value
            }
            return super.getTableCellRendererComponent(table, shown, selected, focused, row, column)
        }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = table
    override fun getName(): String = "Variables"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
    override fun selectNotify() = pull()
}
