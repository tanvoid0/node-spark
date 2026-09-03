package com.nodespark.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nodespark.settings.NodeSparkSettings
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeProjectUtil
import com.nodespark.util.NodeRunnerEntry
import com.nodespark.util.NodeTestDetector
import java.io.File

/**
 * Locates and launches the project's own `typescript-language-server`, which is what actually
 * supplies completion, auto-import, go-to-definition and hover for .js/.ts — Community has no
 * JavaScript PSI of its own, so the answers have to come from tsserver over LSP.
 *
 * Nothing is downloaded or installed: if the project has not put the server in its own
 * node_modules, the feature stays off, exactly like the ESLint and Prettier integrations.
 */
object TsServer {

    /** The npm package and its bin entry share a name. */
    const val PACKAGE = "typescript-language-server"

    /** Must match the server id in node-spark-lsp.xml. */
    const val SERVER_ID = "nodeSparkTypeScript"

    private class Resolved(val root: String, val entry: String?, val at: Long)

    // isEnabled() is called per file event, and resolving reads a package.json off disk, so the
    // answer is held briefly. The TTL is what makes an `npm i` visible without an IDE restart.
    @Volatile private var cache: Resolved? = null
    private const val TTL_MS = 10_000L

    /** Absolute path of the server's entry script under the project's node_modules, or null. */
    fun entry(project: Project): String? {
        val root = NodeProjectUtil.projectRootFor(project)
        val now = System.currentTimeMillis()
        cache?.let { if (it.root == root && now - it.at < TTL_MS) return it.entry }
        val dir = File(root)
        val entry = NodeRunnerEntry.resolveEntry(listOf(PACKAGE), PACKAGE, dir, dir)
        cache = Resolved(root, entry, now)
        return entry
    }

    /** Drops the memoised lookup, so a just-finished install is seen immediately. */
    fun invalidate() {
        cache = null
    }

    /**
     * Whether the server should serve [file]. Off by default; requires a trusted project, because
     * starting it executes a binary out of the repository's own node_modules.
     */
    fun isEnabledFor(project: Project, file: VirtualFile): Boolean =
        NodeSparkSettings.instance.lspEnabled &&
            NodeTestDetector.isJsOrTs(file) &&
            project.isTrusted() &&
            entry(project) != null

    /**
     * `node <cli.mjs> --stdio`, or null when the server is not installed.
     * Spawned through node rather than the node_modules/.bin shim for the reasons in NodeRunnerEntry.
     * The global default env vars are deliberately not applied here: NODE_ENV=test belongs to test
     * runs, not to a long-lived analysis daemon.
     */
    fun commandLine(project: Project): GeneralCommandLine? {
        val entry = entry(project) ?: return null
        return GeneralCommandLine()
            .withWorkDirectory(NodeProjectUtil.projectRootFor(project))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withExePath(NodeCommandLine.nodeExecutable(project))
            .withParameters(entry, "--stdio")
    }
}
