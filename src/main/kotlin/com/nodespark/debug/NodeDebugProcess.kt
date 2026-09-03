package com.nodespark.debug

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.HttpRequests
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.nodespark.util.NodeTestOutputParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

class NodeDebugProcess(
    session: XDebugSession,
    private val processHandler: ProcessHandler,
    private val debugPort: Int,
    private val debugHost: String = "127.0.0.1",
) : XDebugProcess(session) {

    private val console: ConsoleView = NodeTestOutputParser.createConsole(session.project, processHandler)
    private val gson = Gson()
    private val cmdId = AtomicInteger(1)

    @Volatile private var webSocket: WebSocket? = null
    private val pendingCommands = java.util.concurrent.ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private val scriptUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val cdpBreakpointIds = java.util.concurrent.ConcurrentHashMap<XLineBreakpoint<*>, String>()
    // Breakpoints registered before the socket exists: XDebugSessionImpl.initBreakpoints() runs long
    // before connectWithRetry's pooled thread has connected. Without this they are dropped silently.
    private val queuedBreakpoints =
        Collections.synchronizedList(mutableListOf<XLineBreakpoint<XBreakpointProperties<*>>>())

    // ── XDebugProcess contract ──────────────────────────────────────────────

    override fun getEditorsProvider(): XDebuggerEditorsProvider = NodeDebugEditorsProvider()
    override fun createConsole(): ConsoleView = console
    override fun doGetProcessHandler(): ProcessHandler = processHandler

    private val breakpointHandler = object : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
        NodeLineBreakpointType::class.java
    ) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
            if (webSocket == null) queuedBreakpoints.add(breakpoint) else setCdpBreakpoint(breakpoint)
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            queuedBreakpoints.remove(breakpoint)
            val id = cdpBreakpointIds.remove(breakpoint) ?: return
            sendCommand("Debugger.removeBreakpoint", JsonObject().apply { addProperty("breakpointId", id) })
        }
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    override fun startStepOver(context: XSuspendContext?) { sendCommand("Debugger.stepOver") }
    override fun startStepInto(context: XSuspendContext?)  { sendCommand("Debugger.stepInto") }
    override fun startStepOut(context: XSuspendContext?)   { sendCommand("Debugger.stepOut") }
    override fun resume(context: XSuspendContext?)         { sendCommand("Debugger.resume") }

    override fun stop() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "stopped")
        // For an attach session this is a DefaultDebugProcessHandler: destroyProcess() is inert,
        // so nothing we did not spawn gets killed.
        processHandler.destroyProcess()
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
        ApplicationManager.getApplication().executeOnPooledThread { connectWithRetry() }
    }

    // ── Breakpoints ─────────────────────────────────────────────────────────

    private fun setCdpBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        val local = NodeCdpPaths.toLocalPath(breakpoint.fileUrl) ?: run {
            session.setBreakpointInvalid(breakpoint, "Cannot resolve local path for ${breakpoint.fileUrl}")
            return
        }
        val params = JsonObject().apply {
            addProperty("lineNumber", breakpoint.line)          // both CDP and XLineBreakpoint are 0-based
            addProperty("url", NodeCdpPaths.toCdpUrl(local))
            breakpoint.conditionExpression?.expression?.takeIf { it.isNotBlank() }
                ?.let { addProperty("condition", it) }
        }
        sendCommand("Debugger.setBreakpointByUrl", params).thenAccept { resp ->
            val id = resp.getAsJsonObject("result")?.get("breakpointId")?.asString
            if (id == null) {
                val error = resp.getAsJsonObject("error")?.get("message")?.asString ?: "unknown error"
                session.setBreakpointInvalid(breakpoint, error)
                return@thenAccept
            }
            cdpBreakpointIds[breakpoint] = id
            session.setBreakpointVerified(breakpoint)
        }
        // ponytail: CDP may snap the breakpoint to the next executable line; we keep showing the
        // requested one. Read locations[0].lineNumber from the response if that ever matters.
    }

    private fun flushQueuedBreakpoints() {
        val pending = synchronized(queuedBreakpoints) { queuedBreakpoints.toList().also { queuedBreakpoints.clear() } }
        pending.forEach { setCdpBreakpoint(it) }
    }

    internal fun scriptUrl(scriptId: String): String? = scriptUrls[scriptId]

    // ── CDP connection ──────────────────────────────────────────────────────

    private fun connectWithRetry() {
        Thread.sleep(800)
        repeat(12) {
            try {
                val wsUrl = resolveWebSocketUrl() ?: run { Thread.sleep(400); return@repeat }
                connectWebSocket(wsUrl)
                return
            } catch (_: Exception) {
                Thread.sleep(400)
            }
        }
        console.print(
            "[NodeSpark] Could not connect to Node.js debugger at $debugHost:$debugPort. " +
            "Open chrome://inspect to connect manually.\n",
            ConsoleViewContentType.ERROR_OUTPUT
        )
    }

    private fun resolveWebSocketUrl(): String? = try {
        val json = HttpRequests.request("http://$debugHost:$debugPort/json").readString()
        gson.fromJson(json, Array<JsonObject>::class.java)
            .firstOrNull()?.get("webSocketDebuggerUrl")?.asString
    } catch (_: Exception) { null }

    private fun connectWebSocket(wsUrl: String) {
        val connected = CompletableFuture<Unit>()
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(wsUrl), object : WebSocket.Listener {
                private val buf = StringBuilder()

                override fun onOpen(ws: WebSocket) {
                    webSocket = ws
                    ws.request(1)
                    // Node was launched with --inspect-brk and is paused at entry waiting for us.
                    // Order matters: breakpoints must be registered (setBreakpointByUrl) before we
                    // tell it to run, or the first line hit is missed. CDP processes commands on one
                    // connection in send order, so queuing runIfWaitingForDebugger behind the
                    // Debugger.setBreakpointByUrl calls in flushQueuedBreakpoints() is enough — no
                    // need to wait for Debugger.enable's response first.
                    sendCommand("Debugger.enable")
                    sendCommand("Runtime.enable")
                    flushQueuedBreakpoints()
                    sendCommand("Runtime.runIfWaitingForDebugger")
                    console.print(
                        "[NodeSpark] Debugger connected to $debugHost:$debugPort\n",
                        ConsoleViewContentType.SYSTEM_OUTPUT
                    )
                    connected.complete(Unit)
                }

                override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    buf.append(data)
                    if (last) { handleMessage(buf.toString()); buf.clear() }
                    ws.request(1)
                    return null
                }

                override fun onClose(ws: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? = null
                override fun onError(ws: WebSocket, error: Throwable) { connected.completeExceptionally(error) }
            })
        connected.get()
    }

    // ── CDP message dispatch ────────────────────────────────────────────────

    private fun handleMessage(raw: String) {
        try {
            val msg = gson.fromJson(raw, JsonObject::class.java)

            if (msg.has("id")) {
                pendingCommands.remove(msg.get("id").asInt)?.complete(msg)
                return
            }

            val method = msg.get("method")?.asString ?: return
            val params = msg.getAsJsonObject("params")

            when (method) {
                "Debugger.scriptParsed" -> {
                    val id = params?.get("scriptId")?.asString ?: return
                    val url = params.get("url")?.asString ?: return
                    if (url.isNotBlank()) scriptUrls[id] = url
                }
                "Debugger.paused"  -> handlePaused(params)
                "Debugger.resumed" -> { /* session auto-updates */ }

                "Runtime.consoleAPICalled" -> {
                    val type = params?.get("type")?.asString ?: "log"
                    val args = params?.getAsJsonArray("args")
                    val text = args?.joinToString(" ") {
                        it.asJsonObject.get("value")?.asString ?: it.toString()
                    } ?: ""
                    val ct = if (type == "error") ConsoleViewContentType.ERROR_OUTPUT
                              else ConsoleViewContentType.NORMAL_OUTPUT
                    console.print("$text\n", ct)
                }

                "Runtime.exceptionThrown" -> {
                    val detail = params?.getAsJsonObject("exceptionDetails")
                    val text = detail?.get("text")?.asString ?: "Unknown exception"
                    console.print("Exception: $text\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
            }
        } catch (_: Exception) { }
    }

    private fun handlePaused(params: JsonObject?) {
        val frames = params?.getAsJsonArray("callFrames") ?: return
        val topFrame = frames.firstOrNull()?.asJsonObject ?: return
        val suspendCtx = NodeSuspendContext(session, topFrame, this)

        val hitId = params?.getAsJsonArray("hitBreakpoints")?.firstOrNull()?.asString
        val hitBreakpoint = hitId?.let { id -> cdpBreakpointIds.entries.firstOrNull { it.value == id }?.key }

        // --inspect-brk stops on the runner's own first line before any user code is loaded.
        // Surfacing that would park the user inside jest/bin/jest.js every single debug run, so it is
        // resumed straight through — the user's breakpoints are already registered by this point.
        if (hitBreakpoint == null && params?.get("reason")?.asString == "Break on start") {
            sendCommand("Debugger.resume")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (hitBreakpoint != null) {
                if (!session.breakpointReached(hitBreakpoint, null, suspendCtx)) session.resume()
            } else {
                session.positionReached(suspendCtx)
            }
        }
    }

    // ── CDP command sender ──────────────────────────────────────────────────

    internal fun sendCommand(method: String, params: JsonObject? = null): CompletableFuture<JsonObject> {
        val id = cmdId.getAndIncrement()
        val future = CompletableFuture<JsonObject>()
        pendingCommands[id] = future

        val obj = JsonObject().apply {
            addProperty("id", id)
            addProperty("method", method)
            if (params != null) add("params", params)
        }
        webSocket?.sendText(gson.toJson(obj), true)
        return future
    }
}
