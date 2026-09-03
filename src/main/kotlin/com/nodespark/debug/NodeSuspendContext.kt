package com.nodespark.debug

import com.google.gson.JsonObject
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext

class NodeSuspendContext(
    session: XDebugSession,
    topFrame: JsonObject,
    process: NodeDebugProcess,
) : XSuspendContext() {

    private val stack = NodeExecutionStack(topFrame, process)

    override fun getActiveExecutionStack(): XExecutionStack = stack

    override fun computeExecutionStacks(container: XExecutionStackContainer) {
        container.addExecutionStack(listOf(stack), true)
    }
}

class NodeExecutionStack(
    private val topFrame: JsonObject,
    private val process: NodeDebugProcess,
) : XExecutionStack("Node.js") {

    private val frame = NodeStackFrame(topFrame, process)

    override fun getTopFrame(): XStackFrame = frame

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        container.addStackFrames(listOf(frame), true)
    }
}
