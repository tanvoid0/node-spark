package com.nodespark.gutter

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.run.NodeTestConfigurationUtil
import com.nodespark.run.NodeTestRunConfiguration
import com.nodespark.structure.OutlineNode
import com.nodespark.structure.TestKind
import com.nodespark.structure.TestOutline
import com.nodespark.testtree.TestResultStore
import com.nodespark.testtree.TestStatus
import com.nodespark.util.NodeTestDetector
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

/**
 * Run/debug icons in the editor gutter of a test file, drawn straight from the document text.
 *
 * The previous implementation was a `LineMarkerProvider`, which only produces anything when the
 * daemon runs PSI passes over the file. IntelliJ IDEA Community has no JavaScript plugin, TextMate
 * owns `.js` there, and no icon ever appeared — the very IDE this plugin exists for. Nothing about
 * a run icon needs PSI: the describe/it tree already comes from [TestOutline], a pure text parse,
 * so the icons are added to the editor's own markup model instead and work in every IDE.
 */
class NodeTestGutterMarkup : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (!NodeTestDetector.isTestFile(file)) return
        installDocumentListener()
        refresh(editor, project, file.path)
    }

    companion object {
        private val MARKERS = Key.create<List<RangeHighlighter>>("nodespark.gutter.markers")
        private val listenerInstalled = AtomicBoolean(false)

        /**
         * One listener on the event multicaster rather than one per editor: it is registered against
         * a service, so it goes away when the plugin is unloaded on update. A per-editor listener
         * added without a Disposable outlives `editorReleased` for every editor still open at that
         * moment, and holds the plugin's classloader through the Document.
         */
        private fun installDocumentListener() {
            if (!listenerInstalled.compareAndSet(false, true)) return
            val parent = ApplicationManager.getApplication().getService(GutterMarkupDisposable::class.java)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(
                object : BulkAwareDocumentListener.Simple {
                    // A bulk update (Replace in Path, a VCS revert) drops every range marker on the
                    // document and fires no per-change event, so the icons are rebuilt at the end of
                    // it instead.
                    override fun afterDocumentChange(document: Document) = refreshDocument(document)

                    override fun bulkUpdateFinished(document: Document) = refreshDocument(document)
                },
                parent,
            )
        }

        private fun refreshDocument(document: Document) {
            val file = FileDocumentManager.getInstance().getFile(document) ?: return
            if (!NodeTestDetector.isTestFile(file)) return
            for (editor in EditorFactory.getInstance().getEditors(document)) {
                val project = editor.project ?: continue
                if (project.isDisposed) continue
                refresh(editor, project, file.path)
            }
        }

        /** Repaint after a run, so the arrows become ticks and crosses. */
        fun refreshAll(project: Project) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                for (editor in EditorFactory.getInstance().allEditors) {
                    if (editor.project != project) continue
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: continue
                    if (NodeTestDetector.isTestFile(file)) refresh(editor, project, file.path)
                }
            }
        }

        private fun refresh(editor: Editor, project: Project, path: String) {
            if (editor.isDisposed) return
            editor.getUserData(MARKERS)?.forEach { editor.markupModel.removeHighlighter(it) }

            val document = editor.document
            val store = TestResultStore.getInstance(project)
            val added = ArrayList<RangeHighlighter>()

            fun add(node: OutlineNode, ancestors: List<String>) {
                if (node.offset >= document.textLength) return
                val names = ancestors + node.name
                val highlighter = editor.markupModel.addLineHighlighter(
                    null,
                    document.getLineNumber(node.offset),
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                )
                highlighter.gutterIconRenderer = TestGutterIcon(
                    project = project,
                    filePath = path,
                    testName = node.name,
                    namePath = names,
                    isSuite = node.kind == TestKind.DESCRIBE,
                    status = store.get(path, node.name),
                )
                added.add(highlighter)
                node.children.forEach { add(it, names) }
            }

            TestOutline.parse(document.immutableCharSequence).forEach { add(it, emptyList()) }
            editor.putUserData(MARKERS, added)
        }
    }
}

private class TestGutterIcon(
    private val project: Project,
    private val filePath: String,
    /** Leaf name — what the runner is filtered by. */
    private val testName: String,
    /** The describe chain down to [testName] — the label, and the jest/mocha match. */
    private val namePath: List<String>,
    private val isSuite: Boolean,
    private val status: TestStatus?,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = when (status) {
        TestStatus.PASSED -> AllIcons.RunConfigurations.TestState.Green2
        TestStatus.FAILED -> AllIcons.RunConfigurations.TestState.Red2
        TestStatus.SKIPPED -> AllIcons.RunConfigurations.TestState.Yellow2
        null -> NodeSparkIcons.RunTest
    }

    private val fullName: String get() = namePath.joinToString(" > ")

    override fun getTooltipText() = "Run '$fullName'  |  right-click to debug"

    override fun getAccessibleName() = "Run $fullName"

    override fun isNavigateAction() = true

    override fun getAlignment() = Alignment.LEFT

    override fun getClickAction(): AnAction = launch("Run '$fullName'", DefaultRunExecutor.EXECUTOR_ID)

    override fun getPopupMenuActions(): ActionGroup = DefaultActionGroup(
        launch("Run '$fullName'", DefaultRunExecutor.EXECUTOR_ID),
        launch("Debug '$fullName'", DefaultDebugExecutor.EXECUTOR_ID),
    )

    private fun launch(text: String, executorId: String): AnAction = object : AnAction(text, null, null) {
        override fun actionPerformed(e: AnActionEvent) {
            val settings = configuration() ?: return
            val executor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: return
            ExecutionUtil.runConfiguration(settings, executor)
        }
    }

    private fun configuration(): RunnerAndConfigurationSettings? {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration(fullName, NodeTestConfigurationUtil.getType().factory)
        val config = settings.configuration as? NodeTestRunConfiguration ?: return null
        config.testFilePath = filePath
        // The leaf name is what node:test and vitest match; jest and mocha get the whole chain from
        // testNamePath, because they match the full "suite ... test" name.
        config.testNameFilter = testName
        config.suiteFilter = isSuite
        config.testNamePath = namePath
        config.workingDir = project.basePath ?: ""
        // Temporary, not addConfiguration: a gutter click shouldn't leave a permanent Run/Debug entry.
        runManager.setTemporaryConfiguration(settings)
        return settings
    }

    override fun equals(other: Any?): Boolean =
        other is TestGutterIcon && other.filePath == filePath && other.fullName == fullName &&
            other.status == status

    override fun hashCode(): Int = (filePath.hashCode() * 31 + fullName.hashCode()) * 31 + status.hashCode()
}

/** Disposal parent for the document listener: an application service dies with the plugin. */
@Service(Service.Level.APP)
private class GutterMarkupDisposable : Disposable {
    override fun dispose() {}
}
