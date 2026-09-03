package com.nodespark.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "NodeSparkSettings", storages = [Storage("NodeSpark.xml")])
class NodeSparkSettings : PersistentStateComponent<NodeSparkSettings.State> {

    data class State(
        var nodePath: String = "node",
        var npmPath: String = "npm",
        var defaultEnvVars: String = "NODE_ENV=test",
        var autoDetectRunner: Boolean = true,
        var runnerOverride: String = "",  // empty = auto-detect
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var nodePath: String
        get() = state.nodePath
        set(v) { state.nodePath = v }

    var npmPath: String
        get() = state.npmPath
        set(v) { state.npmPath = v }

    var defaultEnvVars: String
        get() = state.defaultEnvVars
        set(v) { state.defaultEnvVars = v }

    var autoDetectRunner: Boolean
        get() = state.autoDetectRunner
        set(v) { state.autoDetectRunner = v }

    var runnerOverride: String
        get() = state.runnerOverride
        set(v) { state.runnerOverride = v }

    companion object {
        val instance: NodeSparkSettings
            get() = ApplicationManager.getApplication().getService(NodeSparkSettings::class.java)
    }
}
