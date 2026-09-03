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
