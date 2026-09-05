package com.nodespark.npm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nodespark.util.NodePackageManager
import java.io.File

/**
 * What a lockfile records about the packages a project depends on, reduced to the one question this
 * plugin asks of it: is it still in step with package.json and with node_modules?
 *
 * Only ranges and resolved versions are read. The transitive graph is the package manager's
 * business — nothing here tries to resolve, satisfy or compare semver.
 */
// ponytail: direct dependencies only, and ranges are compared as exact strings — the same test
// `npm ci` applies before it refuses to run. Add semver satisfaction only if a real report needs it.
object Lockfile {

    /** One resolution recorded in a lockfile: the range that was asked for, and what it became. */
    data class Entry(val range: String, val version: String)

    /**
     * A parsed lockfile. [entries] maps a package name to every resolution recorded for it.
     *
     * [rootScoped] is true when the format separates the root package's own dependencies from the
     * transitive ones, which is what makes "this is in the lockfile but no longer in package.json"
     * answerable. [declaresRanges] is false for formats that record versions only (npm
     * lockfileVersion 1), where a changed range is invisible.
     */
    data class Lock(
        val entries: Map<String, List<Entry>>,
        val rootScoped: Boolean,
        val declaresRanges: Boolean,
        val berry: Boolean = false,
    )

    /**
     * The sections read out of a lockfile's root entry. Deliberately the same two that
     * [NpmScripts.dependenciesOf] reads out of package.json: a lockfile section with no counterpart
     * on the package.json side would read as a package that is locked but no longer declared.
     */
    private val DEP_SECTIONS = listOf("dependencies", "devDependencies")
    private val TRAILING_COMMA = Regex(""",(\s*[}\]])""")
    private val IMPORTERS_LINE = Regex("(?m)^importers:")

    /** Lockfile names [pm] may write, current format first. */
    fun namesFor(pm: NodePackageManager): List<String> = when (pm) {
        NodePackageManager.NPM -> listOf("package-lock.json", "npm-shrinkwrap.json")
        NodePackageManager.YARN -> listOf("yarn.lock")
        NodePackageManager.PNPM -> listOf("pnpm-lock.yaml")
        NodePackageManager.BUN -> listOf("bun.lock", "bun.lockb")
    }

    /** The lockfile [pm] would use in [dir], or null when it has not been written yet. */
    fun find(dir: File, pm: NodePackageManager): File? =
        namesFor(pm).map { File(dir, it) }.firstOrNull { it.isFile }

    /**
     * Lockfiles in [dir] belonging to a manager other than [pm] — usually what a half-finished
     * migration leaves behind, and a real source of "works on my machine".
     */
    fun strays(dir: File, pm: NodePackageManager): List<String> =
        NodePackageManager.values()
            .filter { it != pm }
            .flatMap { namesFor(it) }
            .distinct()
            .filter { File(dir, it).isFile }

    /** bun's binary lockfile is the one format here that cannot be read as text. */
    fun isBinary(name: String): Boolean = name == "bun.lockb"

    /** Never throws: a lockfile mid-write, or in a format newer than this, parses to nothing. */
    fun parse(name: String, text: String): Lock = when {
        name == "yarn.lock" ->
            Lock(parseYarn(text), rootScoped = false, declaresRanges = true, berry = "__metadata:" in text)
        name == "pnpm-lock.yaml" -> Lock(parsePnpm(text), rootScoped = true, declaresRanges = true)
        name == "bun.lock" -> Lock(parseBun(text), rootScoped = true, declaresRanges = true)
        isBinary(name) -> Lock(emptyMap(), rootScoped = false, declaresRanges = false)
        else -> parseNpm(text)
    }

    fun parse(file: File): Lock = parse(file.name, runCatching { file.readText() }.getOrDefault(""))

    /**
     * npm. Formats 2 and 3 keep the root's own ranges under `packages[""]` and every resolved
     * version under `packages["node_modules/<name>"]`; format 1 records versions only.
     */
    private fun parseNpm(text: String): Lock = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        val packages = root.getAsJsonObject("packages")
            ?: return@runCatching Lock(legacyNpm(root), rootScoped = false, declaresRanges = false)

        val versions = HashMap<String, String>()
        for ((path, value) in packages.entrySet()) {
            if (!path.startsWith("node_modules/")) continue
            // A nested copy under another package, not the hoisted one the root resolves to.
            if ("/node_modules/" in path) continue
            val version = (value as? JsonObject)?.get("version")?.asString ?: continue
            versions[path.removePrefix("node_modules/")] = version
        }

        val self = packages.getAsJsonObject("")
        val entries = LinkedHashMap<String, List<Entry>>()
        for (section in DEP_SECTIONS) {
            for ((name, range) in self?.getAsJsonObject(section)?.entrySet().orEmpty()) {
                entries[name] = listOf(Entry(range.asString, versions[name].orEmpty()))
            }
        }
        Lock(entries, rootScoped = true, declaresRanges = true)
    }.getOrElse { Lock(emptyMap(), rootScoped = false, declaresRanges = false) }

    /** lockfileVersion 1: a nested tree of versions, with no record of what was asked for. */
    private fun legacyNpm(root: JsonObject): Map<String, List<Entry>> =
        root.getAsJsonObject("dependencies")?.entrySet().orEmpty().mapNotNull { (name, value) ->
            (value as? JsonObject)?.get("version")?.asString?.let { name to listOf(Entry("", it)) }
        }.toMap()

    /**
     * pnpm. Read as an indented key path rather than through a YAML parser — the platform ships
     * none, and the two shapes that matter are fixed: `importers -> . -> <section> -> <name> ->
     * specifier|version`, or, in lockfiles written before pnpm 7, the top-level `specifiers` and
     * `dependencies` maps.
     */
    private fun parsePnpm(text: String): Map<String, List<Entry>> {
        val hasImporters = IMPORTERS_LINE.containsMatchIn(text)
        val ranges = LinkedHashMap<String, String>()
        val versions = LinkedHashMap<String, String>()
        val stack = ArrayList<Pair<Int, String>>()

        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
            val indent = raw.indexOfFirst { !it.isWhitespace() }
            val trimmed = raw.trim()
            if (trimmed.startsWith("- ")) continue
            val colon = trimmed.indexOf(':')
            if (colon < 0) continue
            val key = trimmed.substring(0, colon).trim().trim('\'', '"')
            val value = trimmed.substring(colon + 1).trim().trim('\'', '"')

            while (stack.isNotEmpty() && stack[stack.size - 1].first >= indent) stack.removeAt(stack.size - 1)
            val path = stack.map { it.second } + key
            stack.add(indent to key)

            if (hasImporters) {
                if (path.size != 5 || path[0] != "importers" || path[1] != "." || path[2] !in DEP_SECTIONS) continue
                when (key) {
                    "specifier" -> ranges[path[3]] = value
                    "version" -> versions[path[3]] = cleanPnpmVersion(value)
                }
            } else {
                if (path.size != 2) continue
                when (path[0]) {
                    "specifiers" -> ranges[key] = value
                    in DEP_SECTIONS -> versions[key] = cleanPnpmVersion(value)
                }
            }
        }
        return (ranges.keys + versions.keys).associateWith {
            listOf(Entry(ranges[it].orEmpty(), versions[it].orEmpty()))
        }
    }

    /** `18.3.1(react@18.3.1)` — pnpm appends the peers a resolution was made against. */
    private fun cleanPnpmVersion(value: String): String = value.substringBefore('(').trim()

    /**
     * yarn, classic and berry. Both list every package in the tree rather than just the root's own,
     * so a dependency dropped from package.json cannot be told apart from a transitive one — hence
     * [Lock.rootScoped] false. What they do answer exactly is whether a declared range was resolved.
     */
    private fun parseYarn(text: String): Map<String, List<Entry>> {
        val out = LinkedHashMap<String, MutableList<Entry>>()
        var headers = emptyList<Pair<String, String>>()
        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
            if (!raw[0].isWhitespace()) {
                headers = raw.trimEnd().removeSuffix(":").split(",")
                    .mapNotNull { splitNameRange(it.trim().trim('"')) }
                continue
            }
            if (headers.isEmpty()) continue
            val trimmed = raw.trim()
            if (!trimmed.startsWith("version ") && !trimmed.startsWith("version:")) continue
            val version = trimmed.removePrefix("version").trim().removePrefix(":").trim().trim('"')
            headers.forEach { (name, range) -> out.getOrPut(name) { ArrayList() }.add(Entry(range, version)) }
            headers = emptyList()
        }
        return out
    }

    /** `react@^18.2.0` -> react, ^18.2.0. Berry spells the same key `react@npm:^18.2.0`. */
    private fun splitNameRange(key: String): Pair<String, String>? {
        val at = key.lastIndexOf('@')
        if (at <= 0) return null
        val name = key.substring(0, at)
        // __metadata is berry's own header block, not a package.
        if (name.startsWith("__")) return null
        return name to key.substring(at + 1).removePrefix("npm:")
    }

    /**
     * bun's text lockfile: JSON with trailing commas. Ranges sit under `workspaces[""]`, and every
     * `packages` entry starts with the `name@version` it resolved to.
     */
    private fun parseBun(text: String): Map<String, List<Entry>> = runCatching {
        val root = JsonParser.parseString(text.replace(TRAILING_COMMA, "$1")).asJsonObject
        val versions = HashMap<String, String>()
        for ((name, value) in root.getAsJsonObject("packages")?.entrySet().orEmpty()) {
            val id = (value as? JsonArray)?.takeIf { it.size() > 0 }?.get(0)?.asString ?: continue
            versions[name] = id.substringAfterLast('@')
        }
        val entries = LinkedHashMap<String, List<Entry>>()
        val self = root.getAsJsonObject("workspaces")?.getAsJsonObject("")
        for (section in DEP_SECTIONS) {
            for ((name, range) in self?.getAsJsonObject(section)?.entrySet().orEmpty()) {
                entries[name] = listOf(Entry(range.asString, versions[name].orEmpty()))
            }
        }
        entries
    }.getOrElse { emptyMap() }
}
