package com.nodespark.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

/**
 * Turns the gutter on for plain JS files. No PSI is consulted anywhere in the platform's
 * `canPutBreakpointAt`, which is what makes this workable in Community.
 *
 * Parameterised on `XBreakpointProperties<*>` rather than extending XLineBreakpointTypeBase:
 * the base class uses the raw type, which would force an unchecked cast on the handler's class token.
 */
class NodeLineBreakpointType : XLineBreakpointType<XBreakpointProperties<*>>(
    "nodespark-js-line", "Node.js (NodeSpark)"
) {
    // ponytail: js only — CDP binds setBreakpointByUrl against the url node actually loaded, so a .ts
    // breakpoint would never bind without source-map translation. Widen once source maps are read.
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean =
        file.extension?.lowercase() in setOf("js", "mjs", "cjs", "jsx")

    override fun createBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? = null

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        project: Project,
    ): XDebuggerEditorsProvider = NodeDebugEditorsProvider()
}
