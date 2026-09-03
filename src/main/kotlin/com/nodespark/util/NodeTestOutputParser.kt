package com.nodespark.util

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project

object NodeTestOutputParser {

    /**
     * Plain console that streams raw process output. Used by the CDP debugger, which drives the
     * process itself and has no run-window test tree. Run-window executions instead get the SM
     * pass/fail tree from NodeTestRunState.createConsole() via ServiceMessageTranscoder — no
     * jest-teamcity package needed.
     */
    fun createConsole(project: Project, processHandler: ProcessHandler): ConsoleView {
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(processHandler)
        return console
    }
}
