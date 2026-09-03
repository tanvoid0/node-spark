package com.nodespark.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Registers the project's typescript-language-server with LSP4IJ.
 *
 * Loaded only when the LSP4IJ plugin is installed (see node-spark-lsp.xml); without it the rest of
 * NodeSpark is unaffected.
 */
class TsLanguageServerFactory : LanguageServerFactory {

    // A null command line is unreachable in practice: isEnabled() below refuses every file when the
    // server is not installed, so LSP4IJ never asks for a connection. OSProcessStreamConnectionProvider
    // reports it as a start failure rather than throwing here, where nothing could show it.
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        OSProcessStreamConnectionProvider(TsServer.commandLine(project))

    override fun createClientFeatures(): LSPClientFeatures = object : LSPClientFeatures() {
        override fun isEnabled(file: VirtualFile): Boolean = TsServer.isEnabledFor(project, file)
    }
}
