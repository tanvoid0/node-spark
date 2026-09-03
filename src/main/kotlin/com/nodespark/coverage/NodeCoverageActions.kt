package com.nodespark.coverage

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.nodespark.icons.NodeSparkIcons
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeTestDetector
import com.nodespark.util.NodeTestDetector.TestRunner
import java.io.File

private const val LCOV_RELATIVE = "coverage/lcov.info"

/** Loads coverage/lcov.info (or a file the user picks) and paints the gutters. */
class ShowNodeCoverageAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val defaultLcov = project.basePath?.let { File(it, LCOV_RELATIVE) }
        val lcov = if (defaultLcov != null && defaultLcov.isFile) {
            defaultLcov
        } else {
            val chosen = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor("info"), project, null,
            ) ?: return
            File(chosen.path)
        }
        NodeCoverageService.getInstance(project).load(lcov)
    }
}

/** Removes every coverage stripe. */
class HideNodeCoverageAction : DumbAwareAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { NodeCoverageService.getInstance(it).isActive } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        NodeCoverageService.getInstance(e.project ?: return).clear()
    }
}

/** Runs the detected test runner with its coverage flag, then auto-loads the lcov it wrote. */
class RunTestsWithCoverageAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = project.basePath ?: return
        val runner = NodeTestDetector.detectRunner(root)

        // NOT TestRunner.reporterArgs — JEST's list contains --no-coverage, which would cancel the whole feature.
        val binName = if (runner == TestRunner.MOCHA) "nyc" else runner.binName
        val bin = NodeCommandLine.resolveBin(project, root, binName)
        if (runner == TestRunner.MOCHA && bin == "nyc") {
            // ponytail: mocha needs nyc installed; no auto-install
            Messages.showErrorDialog(project, "Mocha coverage needs nyc: npm install --save-dev nyc", "NodeSpark")
            return
        }

        val cmd = NodeCommandLine.base(project, root)
        if (runner == TestRunner.NODE_TEST) {
            cmd.exePath = NodeCommandLine.nodeExecutable(project)
            File(root, LCOV_RELATIVE).parentFile.mkdirs()   // node will not create the destination dir
            cmd.addParameters(
                "--test", "--experimental-test-coverage",
                "--test-reporter=lcov", "--test-reporter-destination=$LCOV_RELATIVE",
            )
        } else if (NodeCommandLine.isCmdShim(bin)) {
            cmd.exePath = bin
        } else {
            cmd.exePath = NodeCommandLine.nodeExecutable(project)
            cmd.addParameter(bin)
        }
        when (runner) {
            TestRunner.JEST -> cmd.addParameters("--coverage")                             // jest emits lcov by default
            TestRunner.VITEST -> cmd.addParameters("run", "--coverage", "--coverage.reporter=lcov")
            TestRunner.MOCHA -> cmd.addParameters(
                "--reporter=lcovonly", "--report-dir=coverage",
                NodeCommandLine.resolveBin(project, root, "mocha"), "--reporter=spec",
            )
            TestRunner.NODE_TEST -> {}   // flags already added above
        }

        val handler = runCatching { KillableColoredProcessHandler(cmd) }.getOrElse {
            Messages.showErrorDialog(project, "Could not start ${runner.binName}: ${it.message}", "NodeSpark")
            return
        }
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(handler)
        ProcessTerminatedListener.attach(handler)

        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                val lcov = File(root, LCOV_RELATIVE)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    if (event.exitCode == 0 && lcov.isFile) NodeCoverageService.getInstance(project).load(lcov)
                    else Messages.showWarningDialog(project, "No ${lcov.path} was produced (exit ${event.exitCode}).", "NodeSpark")
                }
            }
        })

        RunContentManager.getInstance(project).showRunContent(
            DefaultRunExecutor.getRunExecutorInstance(),
            RunContentDescriptor(console, handler, console.component, "Node Tests with Coverage"),
        )
        handler.startNotify()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.icon = NodeSparkIcons.RunTest
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
