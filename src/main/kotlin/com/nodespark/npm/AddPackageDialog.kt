package com.nodespark.npm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Search the registry and pick what to install. Typing runs a search after a short pause rather
 * than on every keystroke, and each search cancels the last, so holding a key down costs one
 * request rather than one per character.
 */
class AddPackageDialog(project: Project, dir: File) : DialogWrapper(project) {

    private val registry = NpmRegistry.registryFor(dir)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
    private val listModel = DefaultListModel<NpmRegistry.Hit>()
    private var generation = 0

    private val results = JBList(listModel).apply {
        emptyText.text = "Type to search $registry"
        cellRenderer = object : ColoredListCellRenderer<NpmRegistry.Hit>() {
            override fun customizeCellRenderer(
                list: JList<out NpmRegistry.Hit>,
                value: NpmRegistry.Hit,
                index: Int,
                selected: Boolean,
                focused: Boolean,
            ) {
                append(value.name)
                append("  ${value.version}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                if (value.description.isNotEmpty()) {
                    append("  ${value.description}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
        }
    }

    private val search = SearchTextField(false).apply {
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = schedule()
        })
    }

    private val devBox = JBCheckBox("Add as a development dependency")

    init {
        title = "Add Package"
        setOKButtonText("Add")
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = search.textEditor

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(JBUI.scale(520), JBUI.scale(360))
        add(search, BorderLayout.NORTH)
        add(JBScrollPane(results), BorderLayout.CENTER)
        add(devBox.apply { border = JBUI.Borders.emptyTop(6) }, BorderLayout.SOUTH)
    }

    private fun schedule() {
        alarm.cancelAllRequests()
        alarm.addRequest({ runSearch(search.text.trim()) }, DEBOUNCE_MS)
    }

    private fun runSearch(query: String) {
        if (query.isEmpty()) {
            listModel.clear()
            return
        }
        val id = ++generation
        ApplicationManager.getApplication().executeOnPooledThread {
            val hits = NpmRegistry.search(registry, query)
            ApplicationManager.getApplication().invokeLater({
                // A slower earlier search must not overwrite the results of a later one.
                if (id != generation || isDisposed) return@invokeLater
                listModel.clear()
                hits.forEach(listModel::addElement)
                if (!listModel.isEmpty) results.selectedIndex = 0
            }, com.intellij.openapi.application.ModalityState.stateForComponent(results))
        }
    }

    /**
     * The selected packages, or whatever was typed when nothing is selected — a known name, or a
     * `name@version`, is quicker to type than to search for.
     */
    fun chosen(): List<String> {
        val selected = results.selectedValuesList.map { it.name }
        return selected.ifEmpty { search.text.trim().split(" ").filter { it.isNotEmpty() } }
    }

    fun dev(): Boolean = devBox.isSelected

    private companion object {
        const val DEBOUNCE_MS = 300
    }
}
