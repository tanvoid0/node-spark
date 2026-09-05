package com.nodespark.npm

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.nodespark.notify.PackageInstall
import com.nodespark.sdk.NodeProjectSdkService
import com.nodespark.util.NodePackageManager
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * The "npm" tool window: every package.json in the project with its scripts beneath it, double-click
 * to run — the shape of the Maven tool window, for whichever package manager the project itself uses.
 */
class NpmToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project) = NpmPackages.find(project).isNotEmpty()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = NpmPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(panel, null, false)
        )
    }
}

/** package.json discovery. Pure on [File] so it is testable without a project. */
object NpmPackages {

    private val SKIP = setOf("node_modules", "dist", "build", "out", "target", "coverage", "vendor")

    /** Every package.json at or below [base], outermost first, ignoring build output and dot directories. */
    fun find(base: File, maxDepth: Int = 4): List<File> {
        val found = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            File(dir, "package.json").takeIf { it.isFile }?.let { found += it }
            if (depth >= maxDepth) return
            dir.listFiles()
                ?.filter { it.isDirectory && it.name !in SKIP && !it.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?.forEach { walk(it, depth + 1) }
        }
        if (base.isDirectory) walk(base, 0)
        return found
    }

    fun find(project: Project): List<File> =
        project.basePath?.let { find(File(it)) } ?: emptyList()
}

private fun File.readSafe(): String = runCatching { readText() }.getOrDefault("")

private class PkgNode(project: Project, val packageJson: File) {
    val dir: String = packageJson.parent
    val pm: NodePackageManager = NodeProjectSdkService.getInstance(project).packageManagerFor(dir)
    val label: String = NpmScripts.nameOf(packageJson.readSafe()) ?: packageJson.parentFile.name
    fun scripts(): Map<String, String> = NpmScripts.scriptsOf(packageJson.readSafe())
    fun dependencies(): List<NpmScripts.Dependency> = NpmScripts.dependenciesOf(packageJson.readSafe())
}

private class ScriptNode(val pkg: PkgNode, val script: String, val command: String)

private class GroupNode(val label: String)

private class DependencyNode(val pkg: PkgNode, val dep: NpmScripts.Dependency) {
    /** The installed copy's package.json, when the dependency is actually on disk. */
    fun installed(): File? =
        File(pkg.dir, "node_modules/${dep.name}/package.json").takeIf { it.isFile }
}

private class NpmPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = Renderer()
    }

    init {
        setContent(JBScrollPane(tree))
        toolbar = buildToolbar(tree)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = activateSelection()
        }.installOn(tree)

        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && activateSelection()) e.consume()
            }
        })

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // An install writes a package.json for every package it unpacks; none of them
                    // is the project's, and rescanning once per file would be thousands of walks.
                    if (events.any { it.path.endsWith("package.json") && "/node_modules/" !in it.path }) {
                        ApplicationManager.getApplication().invokeLater({ reload() }, project.disposed)
                    }
                }
            },
        )

        reload()
    }

    private fun buildToolbar(target: JComponent): JComponent {
        val group = DefaultActionGroup(
            action("Run", "Run the selected script", AllIcons.Actions.Execute, {
                runSelected(DefaultRunExecutor.EXECUTOR_ID)
            }) { tree.selectedUserObject() is ScriptNode },
            action("Debug", "Debug the selected script", AllIcons.Actions.StartDebugger, {
                runSelected(DefaultDebugExecutor.EXECUTOR_ID)
            }) { tree.selectedUserObject() is ScriptNode },
            action("Install", "Install this package's dependencies", AllIcons.Actions.Download, {
                selectedPackage()?.let {
                    PackageInstall.run(project, it.dir, it.pm, it.pm.installArgs(), "${it.pm.binName} install")
                }
                true
            }) { selectedPackage() != null },
            action("Refresh", "Rescan package.json files", AllIcons.Actions.Refresh, { reload(); true }) { true },
        )
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
            .also { it.targetComponent = target }
            .component
    }

    private fun action(
        text: String,
        description: String,
        icon: Icon,
        perform: () -> Unit,
        enabled: () -> Boolean,
    ) = object : AnAction(text, description, icon), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = enabled()
        }

        override fun actionPerformed(e: AnActionEvent) = perform()
    }

    private fun JTree.selectedUserObject(): Any? =
        (selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun selectedPackage(): PkgNode? = when (val node = tree.selectedUserObject()) {
        is PkgNode -> node
        is ScriptNode -> node.pkg
        is DependencyNode -> node.pkg
        else -> null
    }

    private fun runSelected(executorId: String): Boolean {
        val node = tree.selectedUserObject() as? ScriptNode ?: return false
        NpmScriptRunner.run(project, node.pkg.packageJson.path, node.script, executorId)
        return true
    }

    /**
     * Enter / double-click: a script runs, a dependency opens its installed package.json.
     * False when the selection is neither, so the tree keeps its own expand/collapse behaviour.
     */
    private fun activateSelection(): Boolean = when (val node = tree.selectedUserObject()) {
        is ScriptNode -> runSelected(DefaultRunExecutor.EXECUTOR_ID)
        is DependencyNode -> openDependency(node)
        else -> false
    }

    private fun openDependency(node: DependencyNode): Boolean {
        val installed = node.installed() ?: return false
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(installed) ?: return false
        return FileEditorManager.getInstance(project).openFile(file, true).isNotEmpty()
    }

    /** A package.json read off the disk, ready for the tree to be built from without touching it again. */
    private data class Scanned(
        val pkg: PkgNode,
        val scripts: Map<String, String>,
        val dependencies: List<NpmScripts.Dependency>,
    )

    /** Walks the project and reads every package.json off the EDT; the tree is rebuilt on it. */
    private fun reload() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val scanned = NpmPackages.find(project).map { json ->
                val pkg = PkgNode(project, json)
                Scanned(pkg, pkg.scripts(), pkg.dependencies())
            }
            ApplicationManager.getApplication().invokeLater({ populate(scanned) }, project.disposed)
        }
    }

    private fun populate(scanned: List<Scanned>) {
        root.removeAllChildren()
        for ((pkg, scripts, dependencies) in scanned) {
            val pkgNode = DefaultMutableTreeNode(pkg)

            if (scripts.isNotEmpty()) {
                val group = DefaultMutableTreeNode(GroupNode("Scripts"))
                scripts.forEach { (name, command) ->
                    group.add(DefaultMutableTreeNode(ScriptNode(pkg, name, command)))
                }
                pkgNode.add(group)
            }

            if (dependencies.isNotEmpty()) {
                val group = DefaultMutableTreeNode(GroupNode("Dependencies"))
                dependencies.forEach { group.add(DefaultMutableTreeNode(DependencyNode(pkg, it))) }
                pkgNode.add(group)
            }

            root.add(pkgNode)
        }
        model.reload()
        // Packages and their group folders, but not the dependency list itself, which is long.
        TreeUtil.expand(tree, 2)
    }

    override fun dispose() {}

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is PkgNode -> {
                    icon = AllIcons.FileTypes.Json
                    append(node.label)
                    append("  ${node.pm.binName}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is GroupNode -> {
                    icon = AllIcons.Nodes.Folder
                    append(node.label)
                }

                is ScriptNode -> {
                    icon = AllIcons.RunConfigurations.TestState.Run
                    append(node.script)
                    append("  ${node.command}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is DependencyNode -> {
                    icon = AllIcons.Nodes.PpLib
                    append(node.dep.name)
                    append("  ${node.dep.version}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    if (node.dep.dev) append("  dev", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
    }
}
