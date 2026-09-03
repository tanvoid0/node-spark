package com.nodespark.lint

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "NodeSparkLintSettings", storages = [Storage("NodeSpark.xml")])
class LintSettings : PersistentStateComponent<LintSettings.State> {

    data class State(
        var eslintEnabled: Boolean = true,
        var prettierOnSave: Boolean = false,
        // Reformat Code and the format-issue highlight both stay dormant until the project has its
        // own prettier, so defaulting them on costs nothing in a project that does not use it.
        var prettierFormatter: Boolean = true,
        var prettierIssues: Boolean = true,
        var eslintTimeoutMs: Int = 5000,
        var prettierTimeoutMs: Int = 5000,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var eslintEnabled: Boolean
        get() = state.eslintEnabled
        set(v) { state.eslintEnabled = v }

    var prettierOnSave: Boolean
        get() = state.prettierOnSave
        set(v) { state.prettierOnSave = v }

    var prettierFormatter: Boolean
        get() = state.prettierFormatter
        set(v) { state.prettierFormatter = v }

    var prettierIssues: Boolean
        get() = state.prettierIssues
        set(v) { state.prettierIssues = v }

    var eslintTimeoutMs: Int
        get() = state.eslintTimeoutMs
        set(v) { state.eslintTimeoutMs = v }

    var prettierTimeoutMs: Int
        get() = state.prettierTimeoutMs
        set(v) { state.prettierTimeoutMs = v }

    companion object {
        val instance: LintSettings
            get() = ApplicationManager.getApplication().getService(LintSettings::class.java)
    }
}
