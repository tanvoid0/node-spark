package com.nodespark.sdk

import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import java.io.File
import javax.swing.Icon

class NodeJsSdkType : SdkType("Node.js") {

    companion object {
        const val TYPE_ID = "Node.js"

        fun getInstance(): NodeJsSdkType =
            SdkType.findInstance(NodeJsSdkType::class.java)

        private val NODE_BIN = if (SystemInfo.isWindows) "node.exe" else "node"

        /** Common install locations to probe */
        private val SEARCH_PATHS: List<String> = buildList {
            if (SystemInfo.isWindows) {
                add("C:\\Program Files\\nodejs")
                add("C:\\Program Files (x86)\\nodejs")
                System.getenv("APPDATA")?.let { add("$it\\npm") }
                System.getenv("ProgramFiles")?.let { add("$it\\nodejs") }
            } else {
                addAll(listOf("/usr/local/bin", "/usr/bin", "/opt/homebrew/bin",
                    "/opt/local/bin", "${System.getenv("HOME")}/.nvm/versions/node"))
            }
            // nvm on Windows
            System.getenv("NVM_HOME")?.let { add(it) }
            // fnm
            System.getenv("FNM_DIR")?.let { add("$it/aliases/default/bin") }
        }
    }

    // ── SDK validation ──────────────────────────────────────────────────────

    override fun isValidSdkHome(path: String): Boolean =
        File(path, NODE_BIN).exists()

    override fun suggestHomePath(): String? =
        suggestHomePaths().firstOrNull()

    override fun suggestHomePaths(): Collection<String> {
        val found = mutableListOf<String>()

        // 1. which/where node
        findOnPath()?.let { found.add(it) }

        // 2. nvm installs  ~/.nvm/versions/node/*/bin
        val nvmBase = File(System.getProperty("user.home"), ".nvm/versions/node")
        if (nvmBase.exists()) {
            nvmBase.listFiles()
                ?.sortedByDescending { it.name }
                ?.forEach { versionDir ->
                    val bin = File(versionDir, "bin")
                    if (File(bin, NODE_BIN).exists()) found.add(bin.absolutePath)
                }
        }

        // 3. fnm  ~/.local/share/fnm/node-versions/*/installation/bin
        val fnmBase = File(System.getProperty("user.home"), ".local/share/fnm/node-versions")
        if (fnmBase.exists()) {
            fnmBase.listFiles()
                ?.sortedByDescending { it.name }
                ?.forEach { vDir ->
                    val bin = File(vDir, "installation/bin")
                    if (File(bin, NODE_BIN).exists()) found.add(bin.absolutePath)
                }
        }

        // 4. static search paths
        SEARCH_PATHS.forEach { p ->
            if (File(p, NODE_BIN).exists()) found.add(p)
        }

        return found.distinct()
    }

    private fun findOnPath(): String? {
        return try {
            val cmd = if (SystemInfo.isWindows) listOf("where", "node") else listOf("which", "node")
            val proc = ProcessBuilder(cmd).start()
            val out = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor()
            // 'which' gives full path; dirname it to get the folder
            out?.let { File(it).parent }
        } catch (_: Exception) {
            null
        }
    }

    // ── Version detection ───────────────────────────────────────────────────

    override fun getVersionString(sdkHome: String): String? {
        return try {
            val nodeExe = File(sdkHome, NODE_BIN).absolutePath
            val proc = ProcessBuilder(nodeExe, "--version").start()
            val version = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor()
            version // e.g. "v20.11.0"
        } catch (_: Exception) {
            null
        }
    }

    override fun suggestSdkName(currentName: String?, sdkHome: String): String {
        val ver = getVersionString(sdkHome) ?: "unknown"
        return "Node.js $ver"
    }

    // ── UI & persistence ────────────────────────────────────────────────────

    override fun getPresentableName() = "Node.js"

    override fun getIcon(): Icon = com.intellij.icons.AllIcons.Nodes.PpLib

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator,
    ): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun isRootTypeApplicable(type: OrderRootType) = false

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the full path to the node executable inside an SDK home */
    fun getNodeExecutable(sdk: Sdk): String =
        File(sdk.homePath ?: "", NODE_BIN).absolutePath

    /** Returns the full path to a bin script (npm, npx, jest, etc.) */
    fun getBinExecutable(sdk: Sdk, name: String): String {
        val binName = if (SystemInfo.isWindows) "$name.cmd" else name
        val local = File(sdk.homePath ?: "", binName)
        return if (local.exists()) local.absolutePath else name
    }
}
