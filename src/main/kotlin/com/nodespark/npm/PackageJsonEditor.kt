package com.nodespark.npm

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.nodespark.notify.PackageInstall
import com.nodespark.sdk.NodeProjectSdkService
import com.nodespark.util.NodePackageManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent as SwingDocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/** Adds a "Package" tab beside the text editor of a project's package.json. */
class PackageJsonEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name == "package.json" && "/node_modules/" !in file.path

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = PackageJsonEditor(project, file)
    override fun getEditorTypeId(): String = "nodespark-package-json"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

/**
 * package.json as a form: the fields at the top, the scripts and the dependencies as grids, and the
 * state of the lockfile beside them.
 *
 * The file stays the single source of truth. Field and script edits are written straight back
 * through the JSON PSI, so undo, VCS and the text tab keep working and nothing is reformatted.
 * Adding, removing and updating a dependency is handed to the package manager instead of edited
 * here, because those have to move the lockfile and node_modules too.
 */
class PackageJsonEditor(private val project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private data class ScriptRow(var name: String, var command: String, val original: String)

    private data class DepRow(
        val name: String,
        var range: String,
        var dev: Boolean,
        var installed: String? = null,
        var latest: String? = null,
        var drift: String? = null,
    ) {
        fun section() = if (dev) "devDependencies" else "dependencies"
    }

    private val dir: File = File(file.parent?.path ?: project.basePath.orEmpty())
    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private val pm: NodePackageManager = NodeProjectSdkService.getInstance(project).packageManagerFor(dir.path)

    private val scriptRows = ArrayList<ScriptRow>()
    private val depRows = ArrayList<DepRow>()
    private var latest = HashMap<String, String>()
    private var disposed = false
    private var selected = false
    private var writing = false
    private var stale = false
    private var report: LockfileSync.Report? = null

    // --- header ---

    private val nameField = headerField("name")
    private val versionField = headerField("version")
    private val descriptionField = headerField("description")
    private val licenseField = headerField("license")
    private val privateBox = JBCheckBox("Private").apply {
        addActionListener { writeJson(listOf("private"), if (isSelected) "true" else null) }
    }
    private val statusLabel = JBLabel()
    private val syncButton = link("") { runSync() }
    private val detailsButton = link("Details") { showDrifts() }
    private val invalidLabel = JBLabel("package.json has a syntax error — fix it in the text tab").apply {
        foreground = JBColor.RED
        isVisible = false
    }

    // --- scripts ---

    private val scriptModel = object : AbstractTableModel() {
        override fun getRowCount() = scriptRows.size
        override fun getColumnCount() = 2
        override fun getColumnName(column: Int) = if (column == 0) "Script" else "Command"
        override fun isCellEditable(row: Int, column: Int) = true
        override fun getValueAt(row: Int, column: Int): Any =
            if (column == 0) scriptRows[row].name else scriptRows[row].command

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            val text = value?.toString().orEmpty().trim()
            val script = scriptRows[row]
            if (column == 0) {
                if (text.isEmpty() || text == script.name) return
                val previous = script.name
                script.name = text
                if (script.original.isEmpty()) writeJson(listOf("scripts", text), PackageJsonPsi.string(script.command))
                else renameJson(listOf("scripts", previous), text)
            } else {
                if (text == script.command) return
                script.command = text
                if (script.name.isNotEmpty()) writeJson(listOf("scripts", script.name), PackageJsonPsi.string(text))
            }
        }
    }

    private val scriptTable = JBTable(scriptModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        putClientProperty("terminateEditOnFocusLost", true)
        columnModel.getColumn(0).preferredWidth = 140
    }

    // --- dependencies ---

    private val typeCombo = JComboBox(arrayOf(PROD, DEV))

    private val depModel = object : AbstractTableModel() {
        override fun getRowCount() = depRows.size
        override fun getColumnCount() = 5
        override fun getColumnName(column: Int) = COLUMNS[column]
        override fun isCellEditable(row: Int, column: Int) = column == 1 || column == 4
        override fun getValueAt(row: Int, column: Int): Any {
            val dep = depRows[row]
            return when (column) {
                0 -> dep.name
                1 -> dep.range
                2 -> dep.installed ?: NOT_INSTALLED
                3 -> dep.latest ?: ""
                else -> if (dep.dev) DEV else PROD
            }
        }

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            val dep = depRows[row]
            val text = value?.toString().orEmpty().trim()
            if (column == 1) {
                if (text.isEmpty() || text == dep.range) return
                dep.range = text
                writeJson(listOf(dep.section(), dep.name), PackageJsonPsi.string(text))
            } else {
                val dev = text == DEV
                if (dev == dep.dev) return
                val from = dep.section()
                dep.dev = dev
                moveSection(dep.name, from, dep.section(), dep.range)
            }
        }
    }

    private val depTable = JBTable(depModel).apply {
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        putClientProperty("terminateEditOnFocusLost", true)
        autoCreateRowSorter = false
        rowSorter = TableRowSorter(depModel)
        columnModel.getColumn(1).preferredWidth = 100
        columnModel.getColumn(2).cellRenderer = InstalledRenderer()
        columnModel.getColumn(4).cellEditor = DefaultCellEditor(typeCombo)
        columnModel.getColumn(4).maxWidth = 90
    }

    private val filterField = SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter packages"
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: SwingDocumentEvent) = applyFilter()
        })
    }

    private val component: JComponent = JPanel(BorderLayout()).apply {
        add(header(), BorderLayout.NORTH)
        add(
            JBSplitter(true, 0.35f).apply {
                firstComponent = section("Scripts", scriptsPanel())
                secondComponent = section("Dependencies", dependenciesPanel())
            },
            BorderLayout.CENTER,
        )
    }

    init {
        pull()
        document?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    // An edit made from this form has already been applied to the model; rebuilding
                    // the rows underneath the cell editor that made it only loses the selection.
                    if (writing) return
                    // Typing in the text tab must not rebuild a grid nobody is looking at.
                    if (selected) pull() else stale = true
                }
            },
            this,
        )
    }

    // --- layout ---

    private fun header(): JComponent {
        val identity = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(nameField, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                    add(JBLabel("Version:"), BorderLayout.WEST)
                    add(versionField.also { it.columns = 10 }, BorderLayout.CENTER)
                },
                BorderLayout.EAST,
            )
        }
        val licence = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(licenseField, BorderLayout.CENTER)
            add(privateBox, BorderLayout.EAST)
        }
        val status = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            border = JBUI.Borders.emptyTop(4)
            add(statusLabel, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
                    add(detailsButton, BorderLayout.WEST)
                    add(syncButton, BorderLayout.EAST)
                },
                BorderLayout.EAST,
            )
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)
            add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent("Name:", identity)
                    .addLabeledComponent("Description:", descriptionField)
                    .addLabeledComponent("License:", licence)
                    .panel,
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout()).apply {
                    add(invalidLabel, BorderLayout.NORTH)
                    add(status, BorderLayout.CENTER)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun section(title: String, content: JComponent): JComponent = JPanel(BorderLayout()).apply {
        add(JBLabel(title).apply { border = JBUI.Borders.empty(4, 8, 2, 8) }, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }

    private fun scriptsPanel(): JComponent = ToolbarDecorator.createDecorator(scriptTable)
        .setAddAction {
            stopEditing()
            scriptRows.add(ScriptRow("", "", ""))
            scriptModel.fireTableDataChanged()
            val last = scriptRows.size - 1
            scriptTable.setRowSelectionInterval(last, last)
            scriptTable.editCellAt(last, 0)
            scriptTable.editorComponent?.requestFocusInWindow()
        }
        .setRemoveAction {
            stopEditing()
            val row = scriptTable.selectedRow.takeIf { it in scriptRows.indices } ?: return@setRemoveAction
            val script = scriptRows.removeAt(row)
            scriptModel.fireTableDataChanged()
            if (script.name.isNotEmpty()) writeJson(listOf("scripts", script.name), null)
        }
        .addExtraAction(action("Run", AllIcons.Actions.Execute, { runScript(DefaultRunExecutor.EXECUTOR_ID) }) {
            scriptTable.selectedRow in scriptRows.indices
        })
        .addExtraAction(action("Debug", AllIcons.Actions.StartDebugger, { runScript(DefaultDebugExecutor.EXECUTOR_ID) }) {
            scriptTable.selectedRow in scriptRows.indices
        })
        .createPanel()

    private fun dependenciesPanel(): JComponent = JPanel(BorderLayout()).apply {
        add(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(0, 8, 4, 8)
                add(filterField, BorderLayout.CENTER)
            },
            BorderLayout.NORTH,
        )
        add(
            ToolbarDecorator.createDecorator(depTable)
                .setAddAction { addPackages() }
                .setRemoveAction { removePackages() }
                .addExtraAction(action("Update to latest", AllIcons.Actions.Download, { updateSelected() }) {
                    selectedDeps().isNotEmpty()
                })
                .addExtraAction(action("Check for updates", AllIcons.Actions.Refresh, { checkForUpdates() }) { true })
                .createPanel(),
            BorderLayout.CENTER,
        )
    }

    // --- reading the file ---

    /** Document -> form. */
    private fun pull() {
        stale = false
        val text = document?.text ?: return
        val valid = NpmScripts.isObject(text)
        invalidLabel.isVisible = !valid
        scriptTable.isEnabled = valid
        depTable.isEnabled = valid
        // A file being typed into is briefly not parseable; keep showing the last good state.
        if (!valid) return

        set(nameField, NpmScripts.stringField(text, "name"))
        set(versionField, NpmScripts.stringField(text, "version"))
        set(descriptionField, NpmScripts.stringField(text, "description"))
        set(licenseField, NpmScripts.stringField(text, "license"))
        privateBox.isSelected = NpmScripts.booleanField(text, "private")

        stopEditing()
        scriptRows.clear()
        NpmScripts.scriptsOf(text).forEach { (name, command) -> scriptRows.add(ScriptRow(name, command, name)) }
        scriptModel.fireTableDataChanged()

        val keep = selectedDeps().mapTo(HashSet()) { it.name }
        depRows.clear()
        NpmScripts.dependenciesOf(text).forEach { depRows.add(DepRow(it.name, it.version, it.dev, latest = latest[it.name])) }
        depModel.fireTableDataChanged()
        reselect(keep)

        refreshInBackground()
    }

    /**
     * Installed versions and the lockfile report, off the EDT: in a monorepo this is a few hundred
     * stat calls and a multi-megabyte lockfile, which is not something to do while painting.
     */
    private fun refreshInBackground() {
        val names = depRows.map { it.name }
        val stamp = document?.modificationStamp ?: 0L
        ApplicationManager.getApplication().executeOnPooledThread {
            val modules = File(dir, "node_modules")
            val installed = names.associateWith { NpmScripts.installedVersion(modules, it) }
            val syncReport = LockfileSyncService.getInstance(project).report(dir.path)
            ApplicationManager.getApplication().invokeLater({
                if (disposed || document?.modificationStamp != stamp) return@invokeLater
                report = syncReport
                val drifts = syncReport.drifts.groupBy { it.name }
                depRows.forEach { row ->
                    row.installed = installed[row.name]
                    row.drift = drifts[row.name]?.joinToString("; ") { it.kind.label }
                }
                depModel.fireTableDataChanged()
                showStatus(syncReport)
            }, project.disposed)
        }
    }

    private fun showStatus(syncReport: LockfileSync.Report) {
        val strays = syncReport.strays.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it }
            ?.let { "  •  foreign lockfile: $it" }
            .orEmpty()
        statusLabel.text = "${pm.binName}  •  ${syncReport.summary()}$strays"
        statusLabel.icon = when {
            syncReport.inSync -> AllIcons.General.InspectionsOK
            syncReport.readable -> AllIcons.General.Warning
            else -> AllIcons.General.Information
        }
        detailsButton.isVisible = syncReport.drifts.isNotEmpty()
        syncButton.isVisible = syncReport.needsInstall || syncReport.needsFrozen
        syncButton.text = "Run ${(listOf(pm.binName) + syncArgs(syncReport)).joinToString(" ")}"
    }

    private fun syncArgs(syncReport: LockfileSync.Report): List<String> =
        if (syncReport.needsFrozen) pm.frozenInstallArgs(syncReport.berry) else pm.installArgs()

    // --- writing the file ---

    private fun jsonFile(): JsonFile? {
        val doc = document ?: return null
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        return PsiManager.getInstance(project).findFile(file) as? JsonFile
    }

    private fun writeJson(path: List<String>, raw: String?) = withWrite { json ->
        PackageJsonPsi.write(project, json, path, raw)
    }

    private fun renameJson(path: List<String>, name: String) = withWrite { json ->
        PackageJsonPsi.rename(project, json, path, name)
    }

    /** A dependency changing between runtime and development is a delete and an insert. */
    private fun moveSection(name: String, from: String, to: String, range: String) = withWrite { json ->
        PackageJsonPsi.write(project, json, listOf(from, name), null)
        PackageJsonPsi.write(project, json, listOf(to, name), PackageJsonPsi.string(range))
    }

    private fun withWrite(edit: (JsonFile) -> Unit) {
        val json = jsonFile() ?: return
        writing = true
        try {
            edit(json)
        } finally {
            writing = false
        }
        refreshInBackground()
    }

    private fun headerField(field: String): JBTextField = JBTextField().apply {
        addActionListener { commit(field, text) }
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = commit(field, text)
        })
    }

    private fun commit(field: String, value: String) {
        val text = document?.text ?: return
        if (!NpmScripts.isObject(text)) return
        val current = NpmScripts.stringField(text, field).orEmpty()
        val trimmed = value.trim()
        if (trimmed == current) return
        writeJson(listOf(field), trimmed.takeIf { it.isNotEmpty() }?.let(PackageJsonPsi::string))
    }

    // --- package manager actions ---

    private fun runPm(args: List<String>) {
        val label = (listOf(pm.binName) + args).joinToString(" ")
        PackageInstall.run(project, dir.path, pm, args, label) {
            LockfileSyncService.getInstance(project).invalidate(dir.path)
        }
    }

    private fun runSync() = report?.let { runPm(syncArgs(it)) }

    private fun showDrifts() {
        val current = report ?: return
        val lines = current.drifts.take(DETAIL_LIMIT).joinToString("\n") { "  $it" }
        val more = (current.drifts.size - DETAIL_LIMIT).takeIf { it > 0 }?.let { "\n  … and $it more" }.orEmpty()
        Messages.showInfoMessage(project, "${current.summary()}:\n\n$lines$more", "Dependencies Out Of Sync")
    }

    private fun addPackages() {
        val dialog = AddPackageDialog(project, dir)
        if (!dialog.showAndGet()) return
        val chosen = dialog.chosen()
        if (chosen.isEmpty()) return
        runPm(pm.addArgs(chosen, dialog.dev()))
    }

    private fun removePackages() {
        val chosen = selectedDeps()
        if (chosen.isEmpty()) return
        val names = chosen.map { it.name }
        val confirmed = Messages.showYesNoDialog(
            project,
            "Remove ${names.joinToString(", ")} from package.json, the lockfile and node_modules?",
            "Remove Dependencies",
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return
        runPm(pm.removeArgs(names))
    }

    /** `add name@latest` is the one spelling all four managers agree on for "move this forward". */
    private fun updateSelected() {
        val chosen = selectedDeps()
        if (chosen.isEmpty()) return
        chosen.groupBy { it.dev }.forEach { (dev, deps) ->
            runPm(pm.addArgs(deps.map { "${it.name}@latest" }, dev))
        }
    }

    /**
     * Fills the "Latest" column. Explicit, never automatic: this is the only thing in the editor
     * that reaches the network, and a project with two hundred dependencies should not do it
     * because a tab was opened.
     */
    private fun checkForUpdates() {
        val names = depRows.map { it.name }
        if (names.isEmpty()) return
        val registry = NpmRegistry.registryFor(dir)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking npm for newer versions", true) {
            override fun run(indicator: ProgressIndicator) {
                val pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS)
                val found = java.util.concurrent.ConcurrentHashMap<String, String>()
                val done = java.util.concurrent.atomic.AtomicInteger()
                try {
                    names.map { name ->
                        pool.submit {
                            if (indicator.isCanceled) return@submit
                            NpmRegistry.latest(registry, name)?.let { found[name] = it }
                            indicator.fraction = done.incrementAndGet().toDouble() / names.size
                        }
                    }.forEach { runCatching { it.get() } }
                } finally {
                    pool.shutdown()
                    pool.awaitTermination(1, TimeUnit.SECONDS)
                }
                if (indicator.isCanceled) return
                ApplicationManager.getApplication().invokeLater({
                    if (disposed) return@invokeLater
                    latest = HashMap(found)
                    depRows.forEach { it.latest = found[it.name] }
                    depModel.fireTableDataChanged()
                }, project.disposed)
            }
        })
    }

    private fun runScript(executorId: String) {
        stopEditing()
        val row = scriptTable.selectedRow.takeIf { it in scriptRows.indices } ?: return
        val name = scriptRows[row].name
        if (name.isNotEmpty()) NpmScriptRunner.run(project, file.path, name, executorId)
    }

    // --- table plumbing ---

    private fun selectedDeps(): List<DepRow> =
        depTable.selectedRows.map { depTable.convertRowIndexToModel(it) }.filter { it in depRows.indices }.map { depRows[it] }

    private fun reselect(names: Set<String>) {
        if (names.isEmpty()) return
        depRows.forEachIndexed { index, row ->
            if (row.name in names) {
                val view = depTable.convertRowIndexToView(index)
                if (view >= 0) depTable.addRowSelectionInterval(view, view)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyFilter() {
        val text = filterField.text.trim()
        val sorter = depTable.rowSorter as TableRowSorter<AbstractTableModel>
        sorter.rowFilter = if (text.isEmpty()) null else RowFilter.regexFilter("(?i)${Regex.escape(text)}", 0)
    }

    private fun set(field: JBTextField, value: String?) {
        val text = value.orEmpty()
        if (field.text != text) field.text = text
    }

    private fun stopEditing() {
        if (scriptTable.isEditing) scriptTable.cellEditor?.stopCellEditing()
        if (depTable.isEditing) depTable.cellEditor?.stopCellEditing()
    }

    private fun link(text: String, perform: () -> Unit) =
        ActionLink(text, java.awt.event.ActionListener { perform() }).apply { isVisible = false }

    private fun action(text: String, icon: Icon, perform: () -> Unit, enabled: () -> Boolean) =
        object : AnAction(text, text, icon), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = enabled()
            }

            override fun actionPerformed(e: AnActionEvent) = perform()
        }

    /** Greys out a package that is not installed, and hangs the drift explanation off the cell. */
    private inner class InstalledRenderer : javax.swing.table.DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            selected: Boolean,
            focused: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, selected, focused, row, column)
            val dep = depRows.getOrNull(table.convertRowIndexToModel(row))
            toolTipText = dep?.drift
            if (!selected) {
                foreground = when {
                    dep?.drift != null -> JBColor.RED
                    dep?.installed == null -> JBColor.GRAY
                    else -> table.foreground
                }
            }
            return component
        }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = depTable
    override fun getName(): String = "Package"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() {
        disposed = true
    }

    override fun selectNotify() {
        selected = true
        if (stale) pull() else refreshInBackground()
    }

    override fun deselectNotify() {
        selected = false
    }

    private companion object {
        const val PROD = "prod"
        const val DEV = "dev"
        const val NOT_INSTALLED = "not installed"
        const val DETAIL_LIMIT = 30
        const val PARALLEL_REQUESTS = 6
        val COLUMNS = arrayOf("Package", "Range", "Installed", "Latest", "Type")
    }
}
