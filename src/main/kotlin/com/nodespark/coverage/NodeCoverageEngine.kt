package com.nodespark.coverage

import com.intellij.coverage.CoverageAnnotator
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageViewExtension
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.coverage.view.DirectoryCoverageViewExtension
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.nodespark.run.NodeTestRunConfiguration
import java.io.File

/**
 * Registers Node coverage with the IDE's own coverage machinery, which is what turns it from a
 * menu action into a feature: a Run with Coverage button beside Run and Debug on a Node test
 * configuration, the Coverage tool window with per-file and per-directory percentages, and the
 * platform's gutter stripes.
 */
class NodeCoverageEngine : NodeCoverageEngineBase() {

    override fun getPresentableText(): String = "NodeSpark"

    override fun isApplicableTo(conf: RunConfigurationBase<*>): Boolean = conf is NodeTestRunConfiguration

    override fun createCoverageEnabledConfiguration(conf: RunConfigurationBase<*>): CoverageEnabledConfiguration =
        NodeCoverageEnabledConfiguration(conf)

    override fun createEmptyCoverageSuite(coverageRunner: CoverageRunner): CoverageSuite = NodeCoverageSuite()

    override fun getCoverageAnnotator(project: Project): CoverageAnnotator =
        NodeCoverageAnnotator.getInstance(project)

    override fun coverageEditorHighlightingApplicableTo(psiFile: PsiFile): Boolean =
        psiFile.virtualFile?.extension in NODE_SOURCE_EXTENSIONS

    override fun acceptedByFilters(psiFile: PsiFile, suite: CoverageSuitesBundle): Boolean = true

    /**
     * Whether a file gets a percentage in the Coverage tool window and the Project view. The default
     * is false — which is why an engine can paint every gutter correctly and still show an empty
     * coverage tree.
     */
    override fun coverageProjectViewStatisticsApplicableTo(outputFile: VirtualFile): Boolean =
        !outputFile.isDirectory && outputFile.extension in NODE_SOURCE_EXTENSIONS

    /**
     * The engine's name for a source file. This is the only agreement between the report and the
     * editor: [NodeCoverageRunner] names its `ClassData` with the same key, so both sides go
     * through [LcovParser.key] and nothing else.
     */
    override fun getQualifiedNames(sourceFile: PsiFile): Set<String> {
        val path = sourceFile.virtualFile?.path ?: return emptySet()
        return setOf(LcovParser.key(path, baseDir(sourceFile.project)))
    }

    /**
     * The name for the compiled artifact of a source file. There is no compilation step here, so it
     * is the same key as [getQualifiedNames] — but the editor annotator asks THIS first (the default
     * "output file" of a source file is the file itself) and paints nothing when it returns null,
     * which is what an unimplemented Node coverage engine looks like: percentages everywhere and not
     * one stripe in the gutter. 2026.2 asks via the java.nio.Path overload, whose own default
     * delegates here.
     */
    public override fun getQualifiedName(outputFile: File, sourceFile: PsiFile): String? =
        getQualifiedNames(sourceFile).firstOrNull()

    override fun nodeViewExtension(
        project: Project,
        suiteBundle: CoverageSuitesBundle,
        stateBean: CoverageViewManager.StateBean,
    ): CoverageViewExtension =
        DirectoryCoverageViewExtension(project, getCoverageAnnotator(project), suiteBundle, stateBean)

    companion object {
        fun baseDir(project: Project): File = File(project.basePath ?: ".")
    }
}
