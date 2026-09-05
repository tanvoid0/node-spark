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
 * The parts of [NodeCoverageEngine] that cannot be written once for both build variants, in their
 * 2024.1-2025.x form. 2026.2 changed two things: the Coverage tool window moved from the
 * three-argument `createCoverageViewExtension` to a two-argument one that does not exist here at
 * all, and the suite factories' `project` parameter became nullable. Either difference alone would
 * make a single `override` wrong on one branch, so each variant carries its own base class and the
 * shared engine implements [nodeViewExtension].
 *
 * See gradle.properties for how the two variants are built, and build.gradle.kts for the source
 * directory each one adds.
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
        project: Project,
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
        stateBean: CoverageViewManager.StateBean,
    ): CoverageViewExtension = nodeViewExtension(project, suiteBundle, stateBean)

    /**
     * Deliberately not `override`: 2024.1 has no such method, and this variant compiles against it.
     * From 2024.3 on — still inside this variant's 241-261 range — the Coverage tool window calls
     * this two-argument form and never the one above, and the JVM dispatches to a method by name
     * and descriptor whether or not the compiler knew the supertype declared it. Without it the
     * tool window is empty on every IDE from 2024.3 to 2026.1.
     */
    fun createCoverageViewExtension(
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
