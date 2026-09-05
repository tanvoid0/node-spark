package com.nodespark.npm

import com.google.gson.JsonParser
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** package.json script extraction. The pure String form is the testable core; the PSI walk is the editor adapter. */
object NpmScripts {

    /** `{"scripts": {"build": "tsc"}}` -> `{build=tsc}`. Never throws; malformed or scripts-less JSON gives an empty map. */
    fun scriptsOf(packageJsonText: String): Map<String, String> = runCatching {
        JsonParser.parseString(packageJsonText).asJsonObject
            .getAsJsonObject("scripts")
            .entrySet().associate { it.key to it.value.asString }
    }.getOrElse { emptyMap() }

    /** One entry of `dependencies` / `devDependencies`. */
    data class Dependency(val name: String, val version: String, val dev: Boolean)

    /** Runtime dependencies first, then dev ones. Never throws; malformed JSON gives an empty list. */
    fun dependenciesOf(packageJsonText: String): List<Dependency> = runCatching {
        val root = JsonParser.parseString(packageJsonText).asJsonObject
        fun section(key: String, dev: Boolean) =
            root.getAsJsonObject(key)?.entrySet()?.map { Dependency(it.key, it.value.asString, dev) }
                ?: emptyList()
        section("dependencies", false) + section("devDependencies", true)
    }.getOrElse { emptyList() }

    /** Top-level `"name"` of a package.json, or null when absent/malformed. */
    fun nameOf(packageJsonText: String): String? = stringField(packageJsonText, "name")

    /** True when the text parses as a JSON object — i.e. it is worth reading fields out of. */
    fun isObject(packageJsonText: String): Boolean =
        runCatching { JsonParser.parseString(packageJsonText).isJsonObject }.getOrElse { false }

    /** A top-level string field of a package.json, or null when absent, blank or malformed. */
    fun stringField(packageJsonText: String, field: String): String? = runCatching {
        JsonParser.parseString(packageJsonText).asJsonObject.get(field).asString.ifBlank { null }
    }.getOrNull()

    /** A top-level boolean field, defaulting to false for absent, non-boolean or malformed. */
    fun booleanField(packageJsonText: String, field: String): Boolean = runCatching {
        JsonParser.parseString(packageJsonText).asJsonObject.get(field).asBoolean
    }.getOrElse { false }

    /**
     * Version of [name] as actually installed under [nodeModules], or null when it is not there.
     * A scoped name is a nested path, which is what the layout on disk already is.
     */
    fun installedVersion(nodeModules: java.io.File, name: String): String? {
        val manifest = java.io.File(nodeModules, "$name/package.json")
        if (!manifest.isFile) return null
        return stringField(runCatching { manifest.readText() }.getOrDefault(""), "version")
    }

    /**
     * Script name if [element] sits on (or inside) a property of the top-level "scripts" object
     * of a file named package.json. Shared by the gutter marker and the run-config producer so both
     * agree on what a script is.
     */
    fun scriptNameOf(element: PsiElement): String? {
        if (element.containingFile?.name != "package.json") return null
        val prop = PsiTreeUtil.getParentOfType(element, JsonProperty::class.java, false) ?: return null
        return scriptNameOfProperty(prop)
    }

    /** As [scriptNameOf] but for an already-resolved property. */
    fun scriptNameOfProperty(prop: JsonProperty): String? {
        val scriptsObj = prop.parent as? JsonObject ?: return null
        val scriptsProp = scriptsObj.parent as? JsonProperty ?: return null
        if (scriptsProp.name != "scripts") return null
        val root = scriptsProp.parent as? JsonObject ?: return null
        if ((prop.containingFile as? JsonFile)?.topLevelValue !== root) return null
        return prop.name
    }
}
