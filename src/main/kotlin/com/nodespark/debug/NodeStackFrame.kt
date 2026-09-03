package com.nodespark.debug

import com.google.gson.JsonObject
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace

class NodeStackFrame(
    private val frame: JsonObject,
    private val process: NodeDebugProcess,
) : XStackFrame() {

    private val functionName = frame.get("functionName")?.asString?.ifBlank { "(anonymous)" } ?: "(anonymous)"

    override fun getSourcePosition(): XSourcePosition? {
        val loc = frame.getAsJsonObject("location") ?: return null
        val url = loc.get("scriptId")?.asString?.let { process.scriptUrl(it) } ?: return null
        val path = NodeCdpPaths.toLocalPath(url) ?: return null           // null for node: internals
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return XDebuggerUtil.getInstance().createPosition(file, loc.get("lineNumber")?.asInt ?: 0)
    }

    override fun customizePresentation(component: ColoredTextContainer) {
        component.append(functionName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        val loc = frame.getAsJsonObject("location")
        val line = (loc?.get("lineNumber")?.asInt ?: 0) + 1
        component.append(":$line", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    override fun computeChildren(node: XCompositeNode) {
        val scopeChain = frame.getAsJsonArray("scopeChain") ?: run {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }

        val list = XValueChildrenList()

        scopeChain.forEach { scopeEl ->
            val scope = scopeEl.asJsonObject
            val type = scope.get("type")?.asString ?: return@forEach
            val objId = scope.getAsJsonObject("object")?.get("objectId")?.asString ?: return@forEach

            // Only show local + closure scopes to reduce noise
            if (type !in listOf("local", "closure", "block")) return@forEach

            val params = JsonObject()
            params.addProperty("objectId", objId)

            process.sendCommand("Runtime.getProperties", params).thenAccept { resp ->
                val result = resp.getAsJsonObject("result")?.getAsJsonArray("result") ?: return@thenAccept
                result.forEach { propEl ->
                    val prop = propEl.asJsonObject
                    val name = prop.get("name")?.asString ?: return@forEach
                    val value = prop.getAsJsonObject("value")
                    list.add(name, NodeValue(name, value, process))
                }
                node.addChildren(list, true)
            }
        }

        if (scopeChain.size() == 0) node.addChildren(XValueChildrenList.EMPTY, true)
    }
}

class NodeValue(
    private val name: String,
    private val valueObj: JsonObject?,
    private val process: NodeDebugProcess,
) : XValue() {

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val type = valueObj?.get("type")?.asString ?: "undefined"
        val value = valueObj?.get("value")?.asString
            ?: valueObj?.get("description")?.asString
            ?: type

        val icon = when (type) {
            "function" -> AllIcons.Nodes.Function
            "object" -> AllIcons.Debugger.Value
            else -> AllIcons.Debugger.Db_primitive
        }
        node.setPresentation(icon, type, value, valueObj?.get("objectId") != null)
    }

    override fun computeChildren(node: XCompositeNode) {
        val objectId = valueObj?.get("objectId")?.asString ?: run {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }

        val params = JsonObject()
        params.addProperty("objectId", objectId)

        process.sendCommand("Runtime.getProperties", params).thenAccept { resp ->
            val result = resp.getAsJsonObject("result")?.getAsJsonArray("result") ?: return@thenAccept
            val list = XValueChildrenList()
            result.take(100).forEach { el ->
                val prop = el.asJsonObject
                val propName = prop.get("name")?.asString ?: return@forEach
                list.add(propName, NodeValue(propName, prop.getAsJsonObject("value"), process))
            }
            node.addChildren(list, true)
        }
    }
}
