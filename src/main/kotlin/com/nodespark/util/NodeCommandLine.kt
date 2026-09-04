package com.nodespark.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.nodespark.sdk.NodeJsSdkType
import com.nodespark.sdk.NodeProjectSdkService
import com.nodespark.settings.NodeSparkSettings
import java.io.File

/** Shared command-line assembly: bin resolution, node lookup, env parsing. Extracted from NodeTestRunState. */
object NodeCommandLine {

    /**
     * Locates [binName]: local node_modules/.bin (with the Windows .cmd shim first),
     * then the SDK's bin dir, then the bare name for PATH lookup.
     * A returned path ending in .cmd MUST be used as the exePath, never as an argument to node.
     */
    @JvmOverloads
    fun resolveBin(project: Project, workDir: String, binName: String, contextFile: String? = null): String {
        // 0. an absolute path typed into Settings -> NodeSpark wins over anything auto-detected:
        // it is the only thing the user asked for explicitly.
        configured(binName)?.let { return it }

        // 1. local node_modules/.bin (highest priority — respects project's pinned version)
        val localBinWin = File(workDir, "node_modules/.bin/$binName.cmd")
        val localBin = File(workDir, "node_modules/.bin/$binName")
        if (localBinWin.exists()) return localBinWin.absolutePath
        if (localBin.exists()) return localBin.absolutePath

        // 2. global bin next to the SDK's node executable
        val sdk = NodeProjectSdkService.getInstance(project).resolvedSdk(contextFile)
        if (sdk != null) {
            val sdkBin = NodeJsSdkType.getInstance().getBinExecutable(sdk, binName)
            if (File(sdkBin).exists()) return sdkBin
        }

        // 3. fall back to PATH
        return onPath(binName)
    }

    /**
     * Absolute path of [name] on PATH, or [name] unchanged when it is not there.
     *
     * A bare name must never reach exePath: GeneralCommandLine sets the working directory to the
     * project root, and Windows CreateProcess searches the current directory before PATH, so a
     * `node.exe` or `npm.cmd` committed to a repository would be executed instead of the real one.
     * findExecutableInPathOnAnyOS searches PATH only, and applies PATHEXT on Windows.
     */
    fun onPath(name: String): String =
        if (File(name).isAbsolute) name
        else PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)?.absolutePath ?: name

    /** node executable: SDK first, then the configured nodePath, then bare "node" on PATH. */
    @JvmOverloads
    fun nodeExecutable(project: Project, contextFile: String? = null): String {
        configured("node")?.let { return it }
        val sdk = NodeProjectSdkService.getInstance(project).resolvedSdk(contextFile)
        if (sdk != null) return NodeJsSdkType.getInstance().getNodeExecutable(sdk)
        return onPath(NodeSparkSettings.instance.nodePath.ifBlank { "node" })
    }

    /**
     * The Settings -> NodeSpark path for [binName], when it is an absolute path to a real file.
     * A bare name there ("node", the old default) is not an override — it says nothing that PATH
     * lookup does not already say — so it falls through to detection.
     */
    private fun configured(binName: String): String? {
        val settings = NodeSparkSettings.instance
        val path = when (binName) {
            "node" -> settings.nodePath
            "npm" -> settings.npmPath
            else -> return null
        }
        return path.takeIf { it.isNotBlank() && File(it).isAbsolute && File(it).isFile }
    }

    /** Parses the comma-separated `KEY=VAL,KEY2=VAL2` form used by the settings and run-config fields. */
    fun parseEnvVars(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
        }.toMap()
    }

    /**
     * A command line with working directory, CONSOLE parent environment and
     * the global default env vars merged with [extraEnv] (extraEnv wins). No exePath yet.
     */
    @JvmOverloads
    fun base(project: Project, workDir: String, extraEnv: Map<String, String> = emptyMap()): GeneralCommandLine {
        val dir = workDir.ifBlank { project.basePath ?: System.getProperty("user.home") }
        return GeneralCommandLine()
            .withWorkDirectory(dir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(parseEnvVars(NodeSparkSettings.instance.defaultEnvVars) + extraEnv)
    }

    /** True if [path] is a Windows batch shim, which must be executed directly rather than passed to node. */
    fun isCmdShim(path: String): Boolean = path.endsWith(".cmd", ignoreCase = true)
}
