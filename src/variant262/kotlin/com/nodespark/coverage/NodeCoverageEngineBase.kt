package com.nodespark.coverage

import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageViewExtension
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.project.Project

/**
 * The 2026.2 half of the split documented in the 241 copy of this file: the Coverage tool window
 * calls the two-argument `createCoverageViewExtension`, and the deprecated suite factories take a
 * nullable project.
 */
@Suppress("DEPRECATION")
abstract class NodeCoverageEngineBase : CoverageEngine() {

    override fun createCoverageSuite(
        covRunner: CoverageRunner,
        name: String,
        coverageDataFileProvider: CoverageFileProvider,
        filters: Array<out String>?,
        lastCoverageTimeStamp: Long,
        suiteToMerge: String?,
        coverageByTestEnabled: Boolean,
        branchCoverage: Boolean,
        trackTestFolders: Boolean,
        project: Project?,
    ): CoverageSuite = NodeCoverageSuite(
        name, coverageDataFileProvider, lastCoverageTimeStamp,
        coverageByTestEnabled, branchCoverage, trackTestFolders, covRunner, project,
    )

    override fun createCoverageSuite(
        covRunner: CoverageRunner,
        name: String,
        coverageDataFileProvider: CoverageFileProvider,
        config: CoverageEnabledConfiguration,
    ): CoverageSuite = NodeCoverageSuite(
        name, coverageDataFileProvider, config.createTimestamp(),
        false, false, true, covRunner, config.configuration.project,
    )

    override fun createCoverageViewExtension(
        project: Project,
        suiteBundle: CoverageSuitesBundle,
    ): CoverageViewExtension =
        nodeViewExtension(project, suiteBundle, CoverageViewManager.getInstance(project).stateBean)

    protected abstract fun nodeViewExtension(
        project: Project,
        suiteBundle: CoverageSuitesBundle,
        stateBean: CoverageViewManager.StateBean,
    ): CoverageViewExtension
}
