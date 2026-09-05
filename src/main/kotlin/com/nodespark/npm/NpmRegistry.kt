package com.nodespark.npm

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests
import java.io.File

/**
 * The npm registry, used for exactly two questions: what packages match what someone is typing, and
 * what the newest version of a package is.
 *
 * Nothing here runs on its own. Both calls are made only when the user asks for them — this plugin
 * promises not to reach out to the network in the background, and an editor that quietly polls a
 * registry for every dependency in a monorepo would break that as well as being slow.
 */
object NpmRegistry {

    const val DEFAULT = "https://registry.npmjs.org"

    private const val TIMEOUT_MS = 8_000
    private val REGISTRY_LINE = Regex("(?m)^\\s*registry\\s*=\\s*(\\S+)")

    /** A search result: enough to choose a package without leaving the dialog. */
    data class Hit(val name: String, val version: String, val description: String)

    /**
     * The registry configured for [dir], from its own .npmrc or the user's, falling back to npm's.
     * A private registry usually needs credentials this does not have, in which case the calls
     * below simply fail and the columns stay empty.
     */
    fun registryFor(dir: File): String {
        val candidates = listOf(File(dir, ".npmrc"), File(System.getProperty("user.home"), ".npmrc"))
        for (file in candidates) {
            if (!file.isFile) continue
            val match = REGISTRY_LINE.find(runCatching { file.readText() }.getOrDefault("")) ?: continue
            return match.groupValues[1].trimEnd('/')
        }
        return DEFAULT
    }

    /** Packages matching [text]. Blocking; call it off the EDT. Any failure gives an empty list. */
    fun search(registry: String, text: String, limit: Int = 20): List<Hit> {
        if (text.isBlank()) return emptyList()
        val url = "$registry/-/v1/search?text=${encode(text)}&size=$limit"
        return parseSearch(get(url) ?: return emptyList())
    }

    /** Newest published version of [name], or null when it cannot be read. Blocking. */
    fun latest(registry: String, name: String): String? {
        val body = get("$registry/${encode(name)}/latest") ?: return null
        return runCatching {
            JsonParser.parseString(body).asJsonObject.get("version").asString
        }.getOrNull()
    }

    /** Split out so the response shape is testable without a network. */
    fun parseSearch(body: String): List<Hit> = runCatching {
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("objects").mapNotNull { element ->
            val pkg = element.asJsonObject.getAsJsonObject("package") ?: return@mapNotNull null
            val name = pkg.string("name") ?: return@mapNotNull null
            Hit(name, pkg.string("version").orEmpty(), pkg.string("description").orEmpty())
        }
    }.getOrElse { emptyList() }

    private fun JsonObject.string(field: String): String? = runCatching { get(field).asString }.getOrNull()

    private fun get(url: String): String? = runCatching {
        HttpRequests.request(url)
            .connectTimeout(TIMEOUT_MS)
            .readTimeout(TIMEOUT_MS)
            .accept("application/json")
            .readString()
    }.getOrNull()

    /** A scoped name is one path segment, so its slash has to survive as an escape. */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
