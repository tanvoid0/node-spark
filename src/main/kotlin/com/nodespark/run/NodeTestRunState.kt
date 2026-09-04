package com.nodespark.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.nodespark.settings.NodeSparkSettings
import com.nodespark.testtree.NodeTestConsoleProperties
import com.nodespark.testtree.recordResultsInto
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeReporters
import com.nodespark.util.NodeRunnerEntry
import com.nodespark.util.NodeTestDetector
import com.nodespark.util.NodeTestDetector.TestRunner
import java.io.File

class NodeTestRunState(
    env: ExecutionEnvironment,
    private val config: NodeTestRunConfiguration,
    private val debugPort: Int = -1,   // -1 = normal run, >0 = debug mode
) : CommandLineState(env) {

    private val project: Project = env.project

    private val workDir = config.workingDir.ifEmpty {
        project.basePath ?: System.getProperty("user.home")
    }

    private val runner: TestRunner = NodeSparkSettings.instance.let { settings ->
        if (!settings.autoDetectRunner && settings.runnerOverride.isNotEmpty()) {
            runCatching { TestRunner.valueOf(settings.runnerOverride.uppercase()) }
                .getOrElse { NodeTestDetector.detectRunner(workDir) }
        } else {
            NodeTestDetector.detectRunner(workDir)
        }
    }

    // CommandLineState.execute() calls startProcess() then createConsole() then wires them.
    // We override both; do NOT override execute() — that caused premature startNotify().
    public override fun startProcess(): ProcessHandler {
        val handler = KillableColoredProcessHandler(buildCommandLine())
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    // execute() attaches the returned console itself — do not use createAndAttachConsole() here or
    // every test event fires twice. Debug runs bypass this entirely (NodeTestDebugProgramRunner
    // calls startProcess() directly) and keep their plain console.
    override fun createConsole(executor: com.intellij.execution.Executor): ConsoleView {
        val console = SMTestRunnerConnectionUtil.createConsole(
            "NodeSpark",
            NodeTestConsoleProperties(config, executor),
        )
        (console as? SMTRunnerConsoleView)?.recordResultsInto(project, config.testFilePath)
        return console
    }

    private fun buildCommandLine(): GeneralCommandLine {
        val cmd = NodeCommandLine.base(project, workDir, NodeCommandLine.parseEnvVars(config.envVars))
        cmd.exePath = NodeCommandLine.nodeExecutable(project, config.testFilePath)
        if (debugPort > 0) cmd.addParameter("--inspect-brk=$debugPort")

        val reporter = NodeReporters.path(runner)

        if (runner == TestRunner.NODE_TEST) {
            cmd.addParameter("--test")
            if (reporter != null) {
                // node resolves --test-reporter as an ESM specifier, and a bare Windows path fails
                // with ERR_UNSUPPORTED_ESM_URL_SCHEME ('D:' reads as a protocol) — pass a file URL.
                cmd.addParameters(
                    "--test-reporter=${File(reporter).toURI()}",
                    "--test-reporter-destination=stdout",
                )
            }
        } else {
            // Spawning the runner's own entry script rather than node_modules/.bin/<name>.cmd:
            // the Windows shim routes arguments through cmd.exe, which expands %FOO% inside a test
            // name pattern and eats ^ — see NodeRunnerEntry.
            val startDir = File(config.testFilePath).parentFile ?: File(workDir)
            val entry = NodeRunnerEntry.resolve(runner, startDir, File(workDir))
            if (entry != null) {
                cmd.addParameter(entry)
            } else {
                // No installed entry script: fall back to the shim and let it be cmd.exe's problem.
                val bin = NodeCommandLine.resolveBin(project, workDir, runner.binName, config.testFilePath)
                if (NodeCommandLine.isCmdShim(bin)) cmd.exePath = bin else cmd.addParameter(bin)
            }
        }

        when (runner) {
            TestRunner.JEST -> {
                if (reporter != null) cmd.addParameter("--reporters=$reporter")
                cmd.addParameter("--no-coverage")
            }
            TestRunner.VITEST -> {
                cmd.addParameter("run")
                if (reporter != null) cmd.addParameter("--reporter=$reporter")
            }
            TestRunner.MOCHA -> if (reporter != null) cmd.addParameter("--reporter=$reporter")
            TestRunner.NODE_TEST -> {}
        }

        // Every runner farms test files out to worker processes by default, and only the process we
        // attached to carries --inspect-brk — so without forcing single-process execution a breakpoint
        // inside a test file can never bind.
        if (debugPort > 0) {
            when (runner) {
                TestRunner.JEST -> cmd.addParameter("--runInBand")
                TestRunner.VITEST -> cmd.addParameter("--no-file-parallelism")
                TestRunner.NODE_TEST -> cmd.addParameter("--test-concurrency=1")
                TestRunner.MOCHA -> {}   // mocha runs in-process already
            }
        }

        if (config.updateSnapshots && (runner == TestRunner.JEST || runner == TestRunner.VITEST)) {
            cmd.addParameter("-u")   // core mocha and node:test have no snapshots
        }

        addFilter(cmd)
        addTestPath(cmd)
        return cmd
    }

    /** Jest takes the path as an explicit file, everyone else as a positional argument. */
    private fun addTestPath(cmd: GeneralCommandLine) {
        if (config.testFilePath.isEmpty()) return
        // A bare positional is a *regex* for jest, so a path containing '.' or '+' would match the
        // wrong files; --runTestsByPath makes it a literal path.
        if (runner == TestRunner.JEST) cmd.addParameter("--runTestsByPath")
        cmd.addParameter(config.testFilePath)
    }

    /**
     * The filter is a literal test name, but jest/mocha/node:test treat it as a regex, so it is
     * escaped — otherwise "adds (2+2)" is read as a group. Anchoring differs per runner and is the
     * easy thing to get wrong: jest and mocha match against the FULL name ("suite ... test"), so a
     * leading ^ makes a leaf-name filter match nothing at all; node:test matches each level's own
     * name, where ^...$ is right. Vitest's -t is a plain substring match, no escaping.
     */
    private fun addFilter(cmd: GeneralCommandLine) {
        val name = config.testNameFilter
        if (name.isBlank()) return
        val escaped = escapeForJsRegex(name)
        // jest and mocha match the full "suite ... test" name, so the whole describe chain goes into
        // the pattern when it is known: "login" alone would also match "auth > login fails".
        // A suite name is a prefix of that full name, so it takes no trailing anchor.
        val end = if (config.suiteFilter) "" else "$"
        val chain = if (config.testNamePath.size > 1) {
            "^" + config.testNamePath.joinToString(".*") { escapeForJsRegex(it) }
        } else {
            escaped
        }
        when (runner) {
            // Trailing anchor only: enough to stop "adds" also running "adds negatives".
            TestRunner.JEST -> cmd.addParameter("--testNamePattern=$chain$end")
            TestRunner.MOCHA -> cmd.addParameter("--grep=$chain$end")
            TestRunner.VITEST -> cmd.addParameters("-t", name)
            // node:test matches each level's own name, so a suite name is anchored like any other.
            TestRunner.NODE_TEST -> cmd.addParameter("--test-name-pattern=^$escaped$")
        }
    }

    /**
     * JS regex escaping. Kotlin's Regex.escape produces a `\Q...\E` quote, which JavaScript's
     * RegExp does not understand, so the metacharacters are escaped one by one instead.
     */
    private fun escapeForJsRegex(name: String): String =
        name.replace(Regex("""[.*+?^$\\{}()|\[\]/]""")) { "\\" + it.value }
}
