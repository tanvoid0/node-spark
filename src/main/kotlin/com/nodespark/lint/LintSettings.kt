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
        var eslintTimeoutMs: Int = 5000,
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

    var eslintTimeoutMs: Int
        get() = state.eslintTimeoutMs
        set(v) { state.eslintTimeoutMs = v }

    companion object {
        val instance: LintSettings
            get() = ApplicationManager.getApplication().getService(LintSettings::class.java)
    }
}
