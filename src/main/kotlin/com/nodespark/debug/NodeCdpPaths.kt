package com.nodespark.debug

import java.io.File
import java.net.URI

/**
 * CDP script urls <-> local paths. Pure, no platform deps, unit-tested.
 *
 * Three url shapes turn up in practice and all must round-trip:
 *   file:///C:/x/y.js   CDP / java.net.URI canonical
 *   file://C:/x/y.js    IntelliJ VFS (XLineBreakpoint.getFileUrl)
 *   file:/C:/x/y.js     java.io.File.toURI()
 */
object NodeCdpPaths {

    private fun normalize(url: String): String = when {
        url.startsWith("file:///") -> url
        url.startsWith("file://") -> "file:///" + url.removePrefix("file://")
        else -> "file:///" + url.removePrefix("file:").trimStart('/')
    }

    /** `C:\x\y.js` -> `file:///C:/x/y.js`; percent-encoding courtesy of java.io.File. */
    fun toCdpUrl(localPath: String): String = normalize(File(localPath).toURI().toString())

    /**
     * `file:///C:/x/y.js` -> `C:/x/y.js`; `file:///home/x/y.js` -> `/home/x/y.js`; null for `node:` urls.
     * Forward slashes on purpose — LocalFileSystem.findFileByPath normalises to them anyway.
     * URI.getPath does the %-decoding; URLDecoder would turn `+` into a space and corrupt `C:/a+b`.
     */
    fun toLocalPath(url: String): String? {
        if (!url.startsWith("file:")) return null
        val raw = try { URI(normalize(url)).path } catch (_: Exception) { null } ?: return null
        // /C:/x/y.js -> C:/x/y.js (Windows drive letters only)
        return if (raw.length > 2 && raw[0] == '/' && raw[2] == ':') raw.substring(1) else raw
    }
}
