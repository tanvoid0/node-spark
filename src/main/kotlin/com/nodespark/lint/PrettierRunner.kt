package com.nodespark.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.nodespark.util.NodeCommandLine
import com.nodespark.util.NodeProjectUtil

/**
 * Shared plumbing for the three prettier entry points: the explicit action, the Reformat Code
 * service and the format-issue annotator.
 *
 * Everything is delegated to the project's own prettier binary, so .prettierrc, .editorconfig,
 * .prettierignore and the `prettier` key in package.json are honoured without this plugin knowing
 * they exist. No prettier in node_modules means null everywhere: nothing is downloaded, nothing is
 * enforced.
 */
object PrettierRunner {

    /** `prettier --stdin-filepath <file>`, or null when the project is untrusted or has no prettier. */
    fun command(project: Project, file: VirtualFile): GeneralCommandLine? {
        // prettier is an executable out of the project's node_modules and it loads the project's
        // config: both are attacker-controlled in a repository the user only cloned, and the
        // annotator and on-save paths reach here with no user gesture.
        if (!project.isTrusted()) return null
        val workDir = NodeProjectUtil.projectRootFor(project, file.path)
        val bin = localBin(workDir, "prettier") ?: return null
        val cmd = NodeCommandLine.base(project, workDir)
        cmd.exePath = bin  // the .cmd shim is the executable, never an argument to node
        // --stdin-filepath is what makes prettier pick the parser and resolve the config as if the
        // text were read from that path, ignore rules included.
        cmd.addParameters("--stdin-filepath", file.path)
        return cmd
    }

    fun isAvailable(project: Project, file: VirtualFile): Boolean = command(project, file) != null

    /**
     * Formatted text, or null on crash/timeout/non-zero exit (a syntax error the user is still
     * typing exits 2). [handler] is already started; the caller keeps it so it can be cancelled.
     */
    fun format(handler: CapturingProcessHandler, text: String, timeoutMs: Int): String? = try {
        // prettier emits nothing until stdin closes, so writing before runProcess cannot deadlock.
        handler.processInput?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val out = handler.runProcess(timeoutMs, true)
        // A Document is always \n-separated, so a config with endOfLine: "crlf" must be normalised
        // before the text goes back into the editor.
        if (out.isTimeout || out.exitCode != 0) null else out.stdout.replace("\r\n", "\n")
    } catch (e: Exception) {
        null
    }

    /** Convenience for callers with nothing to cancel. */
    fun format(cmd: GeneralCommandLine, text: String, timeoutMs: Int): String? =
        try {
            format(CapturingProcessHandler(cmd), text, timeoutMs)
        } catch (e: Exception) {
            null  // the process failed to start at all
        }
}
