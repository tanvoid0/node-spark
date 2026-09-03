package com.nodespark.coverage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.DefaultLineMarkerRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import java.io.File

/** Holds the parsed lcov data and paints covered/uncovered stripes in the editor gutters. */
@Service(Service.Level.PROJECT)
class NodeCoverageService(private val project: Project) {

    private var data: Map<String, Map<Int, Int>> = emptyMap()
    private var baseDir: File? = null

    // ponytail: colour snapshotted at renderer construction (DefaultLineMarkerRenderer resolves it in its ctor);
    // rebuilt on every load(). Subscribe EditorColorsManager.TOPIC if theme switches must repaint live.
    private var coveredRenderer: DefaultLineMarkerRenderer? = null
    private var uncoveredRenderer: DefaultLineMarkerRenderer? = null

    val isActive: Boolean get() = data.isNotEmpty()

    init {
        // There is no editorFactoryListener extension point, so subscribe directly; the service's own
        // lifetime bounds the listener, so an editor opened after load() still gets painted.
        EditorFactory.getInstance().addEditorFactoryListener(
            object : com.intellij.openapi.editor.event.EditorFactoryListener {
                override fun editorCreated(event: com.intellij.openapi.editor.event.EditorFactoryEvent) {
                    if (!project.isDisposed && event.editor.project == project) paint(event.editor)
                }
            },
            project,
        )
    }

    fun load(lcov: File) {
        val text = runCatching { lcov.readText() }.getOrNull() ?: return
        val base = File(project.basePath ?: lcov.parentFile?.parent ?: lcov.absolutePath)
        onEdt {
            stripAll()                       // must happen before the renderer instances are swapped
            coveredRenderer = DefaultLineMarkerRenderer(CodeInsightColors.LINE_FULL_COVERAGE, 3)
            uncoveredRenderer = DefaultLineMarkerRenderer(CodeInsightColors.LINE_NONE_COVERAGE, 3)
            baseDir = base
            data = LcovParser.parse(text, base)
            editors().forEach { paint(it) }
        }
    }

    fun clear() = onEdt {
        stripAll()
        data = emptyMap()
        baseDir = null
    }

    /** Called on editor creation and after a load. No-op when no coverage is loaded. */
    fun paint(editor: Editor) {
        if (editor.isDisposed) return
        val covered = coveredRenderer ?: return
        val uncovered = uncoveredRenderer ?: return
        val base = baseDir ?: return
        val vf = editor.virtualFile ?: return
        val lines = data[LcovParser.key(vf.path, base)] ?: return

        strip(editor)
        val doc = editor.document
        val markup = editor.markupModel
        val sorted = lines.keys.sorted()
        var i = 0
        while (i < sorted.size) {
            val hit = (lines[sorted[i]] ?: 0) > 0
            var j = i
            // merge consecutive lines of equal status into one highlighter
            while (j + 1 < sorted.size &&
                sorted[j + 1] == sorted[j] + 1 &&
                ((lines[sorted[j + 1]] ?: 0) > 0) == hit
            ) j++

            val start = sorted[i] - 1                       // lcov is 1-based, Document is 0-based
            if (start in 0 until doc.lineCount) {           // lcov can be staler than the buffer
                val end = minOf(sorted[j] - 1, doc.lineCount - 1)
                markup.addRangeHighlighter(
                    doc.getLineStartOffset(start), doc.getLineEndOffset(end),
                    HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE,
                ).lineMarkerRenderer = if (hit) covered else uncovered
            }
            i = j + 1
        }
    }

    /** Removes only our highlighters — identified by renderer identity, so no bookkeeping map. */
    private fun strip(editor: Editor) {
        if (editor.isDisposed) return
        val markup = editor.markupModel
        markup.allHighlighters
            .filter { it.lineMarkerRenderer === coveredRenderer || it.lineMarkerRenderer === uncoveredRenderer }
            .forEach { markup.removeHighlighter(it) }
    }

    private fun stripAll() = editors().forEach { strip(it) }

    private fun editors(): List<Editor> =
        EditorFactory.getInstance().allEditors.filter { it.project == null || it.project == project }

    private fun onEdt(body: () -> Unit) = ApplicationManager.getApplication().invokeLater {
        if (!project.isDisposed) body()
    }

    companion object {
        fun getInstance(project: Project): NodeCoverageService =
            project.getService(NodeCoverageService::class.java)
    }
}
