package com.nodespark.coverage

import com.intellij.coverage.BaseCoverageSuite
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.BaseCoverageAnnotator
import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData
import java.io.File

/** Extensions an lcov writer can report line data for. */
internal val NODE_SOURCE_EXTENSIONS = setOf("js", "jsx", "mjs", "cjs", "ts", "tsx", "mts", "cts")

/** Directory names never worth counting, whatever they contain. */
private val SKIPPED_DIRS = setOf("node_modules", "dist", "build", "out", "coverage", "vendor", ".git", ".next")

/** A Node source file outside the directories no one wants percentages for. */
internal fun isCountableSource(file: File): Boolean {
    val name = file.name
    if (name.endsWith(".d.ts") || name.endsWith(".min.js")) return false   // no executable lines
    if (file.extension.lowercase() !in NODE_SOURCE_EXTENSIONS) return false
    return generateSequence(file.parentFile) { it.parentFile }.none { it.name in SKIPPED_DIRS }
}

/**
 * Turns an `lcov.info` into the platform's [ProjectData]. The platform names covered units
 * "classes"; for a Node project a class is a source file, keyed by [LcovParser.key] — the same key
 * `NodeCoverageEngine.getQualifiedNames` hands back for an open editor.
 */
class NodeCoverageRunner : CoverageRunner() {

    override fun getPresentableName(): String = "lcov"

    override fun getId(): String = ID

    override fun getDataFileExtension(): String = "info"

    override fun acceptsCoverageEngine(engine: CoverageEngine): Boolean = engine is NodeCoverageEngine

    override fun loadCoverageData(sessionDataFile: File, baseCoverageSuite: CoverageSuite?): ProjectData? {
        val text = runCatching { sessionDataFile.readText() }.getOrNull() ?: return null
        // Relative SF: paths in the report are resolved against the project, exactly as the engine
        // resolves an editor's file; a suite restored from workspace.xml has no project yet, so the
        // report's own directory stands in.
        val base = baseCoverageSuite?.project?.basePath?.let(::File)
            ?: sessionDataFile.absoluteFile.parentFile
        val parsed = LcovParser.parse(text, base)
        if (parsed.isEmpty()) return null

        val data = ProjectData()
        for ((path, hitsByLine) in parsed) {
            val lines = arrayOfNulls<LineData>((hitsByLine.keys.maxOrNull() ?: 0) + 1)
            for ((line, hits) in hitsByLine) {
                if (line <= 0) continue                    // lcov is 1-based; index 0 stays empty
                lines[line] = LineData(line, null).also { it.hits = hits }
            }
            data.getOrCreateClassData(path).apply {
                setLines(lines)
                source = File(path).name
            }
        }
        return data
    }

    companion object {
        const val ID = "NodeSparkLcov"

        val instance: NodeCoverageRunner get() = getInstance(NodeCoverageRunner::class.java)
    }
}

/**
 * Per-file and per-directory percentages for the Coverage tool window and the Project view.
 * [SimpleCoverageAnnotator] already does all of it for file-keyed coverage data.
 */
@Service(Service.Level.PROJECT)
class NodeCoverageAnnotator(project: Project) : SimpleCoverageAnnotator(project) {

    /**
     * A source file the report never mentions counts as nothing covered. The default is to return
     * null, which leaves the file out of the tree altogether — and a file with no tests at all is
     * exactly the one worth seeing.
     *
     * The denominator is physical lines, not executable ones: only the report knows which lines are
     * executable, and it has nothing to say about this file. So a project whose runner reports only
     * the files it touched (the default for every runner here) reads a little pessimistically. Turn
     * the runner's own "all files" option on — `--coverage.all`, jest's `collectCoverageFrom` — and
     * every count comes from the report instead.
     */
    override fun fillInfoForUncoveredFile(file: File): BaseCoverageAnnotator.FileCoverageInfo? {
        if (!isCountableSource(file)) return null
        val lines = runCatching { file.useLines { seq -> seq.count() } }.getOrNull() ?: return null
        return BaseCoverageAnnotator.FileCoverageInfo().apply { totalLineCount = lines }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): NodeCoverageAnnotator =
            project.getService(NodeCoverageAnnotator::class.java)
    }
}

/**
 * Where a run under the coverage executor is told to write its report. One fixed directory per
 * configuration, outside the project: the file name is what every runner produces
 * (`<dir>/lcov.info`), so the run only has to be pointed at the directory.
 */
class NodeCoverageEnabledConfiguration(config: RunConfigurationBase<*>) :
    CoverageEnabledConfiguration(config, NodeCoverageRunner.instance) {

    override fun createCoverageFile(): String = File(reportDir(configuration), LCOV_NAME).path

    companion object {
        const val LCOV_NAME = "lcov.info"

        fun reportDir(config: RunConfigurationBase<*>): File = File(
            File(PathManager.getSystemPath(), "node-spark-coverage"),
            FileUtil.sanitizeFileName("${config.project.name}-${config.name}"),
        )

        /** The lcov file a coverage run of [config] writes, or null when coverage is not ours. */
        fun reportFile(config: RunConfigurationBase<*>): File? =
            (CoverageEnabledConfiguration.getOrCreate(config) as? NodeCoverageEnabledConfiguration)
                ?.let { File(it.coverageFilePath) }
    }
}

/**
 * A loaded lcov report. The no-argument form is what the IDE restores from `workspace.xml` before
 * calling `readExternal`, so it must exist and must not require a project.
 */
@Suppress("DEPRECATION")
class NodeCoverageSuite : BaseCoverageSuite {

    constructor() : super()

    constructor(
        name: String?,
        fileProvider: CoverageFileProvider?,
        lastCoverageTimeStamp: Long,
        coverageByTestEnabled: Boolean,
        branchCoverage: Boolean,
        trackTestFolders: Boolean,
        runner: CoverageRunner?,
        project: Project?,
    ) : super(
        name, fileProvider, lastCoverageTimeStamp, coverageByTestEnabled, branchCoverage,
        trackTestFolders, runner, project,
    )

    override fun getCoverageEngine(): CoverageEngine =
        CoverageEngine.EP_NAME.findExtensionOrFail(NodeCoverageEngine::class.java)
}
