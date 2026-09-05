package com.nodespark.npm

import com.intellij.json.psi.JsonElementGenerator
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

/**
 * Edits package.json through the JSON PSI rather than by re-serialising it.
 *
 * A round trip through a JSON library would rewrite the whole file — key order, indentation, the
 * blank lines someone put between sections — on every keystroke in the grid. Replacing one PSI
 * value touches one value.
 */
object PackageJsonPsi {

    /** JSON text for a string value, quoted and escaped. */
    fun string(value: String): String = "\"${StringUtil.escapeStringCharacters(value)}\""

    /**
     * Sets the value at [path], creating any missing objects along the way; a null [raw] removes it.
     * [raw] is JSON source, so a string value has to arrive quoted — see [string].
     */
    fun write(project: Project, file: JsonFile, path: List<String>, raw: String?) {
        if (path.isEmpty()) return
        val root = file.topLevelValue as? JsonObject ?: return
        WriteCommandAction.runWriteCommandAction(project, "Edit package.json", null, {
            if (raw == null) {
                remove(root, path)
            } else {
                val generator = JsonElementGenerator(project)
                var parent = root
                for (key in path.dropLast(1)) parent = childObject(generator, parent, key) ?: return@runWriteCommandAction
                set(generator, parent, path.last(), raw)
            }
        }, file)
    }

    /** Renames a key, keeping its value and its position in the file. */
    fun rename(project: Project, file: JsonFile, path: List<String>, name: String) {
        if (path.isEmpty() || name.isEmpty()) return
        val root = file.topLevelValue as? JsonObject ?: return
        WriteCommandAction.runWriteCommandAction(project, "Rename package.json Key", null, {
            var parent = root
            for (key in path.dropLast(1)) parent = parent.child(key) ?: return@runWriteCommandAction
            val property = parent.findProperty(path.last()) ?: return@runWriteCommandAction
            property.nameElement.replace(JsonElementGenerator(project).createStringLiteral(name))
        }, file)
    }

    private fun set(generator: JsonElementGenerator, parent: JsonObject, name: String, raw: String) {
        val existing = parent.findProperty(name)?.value
        if (existing != null) {
            existing.replace(generator.createValue<JsonValue>(raw))
        } else {
            JsonPsiUtil.addProperty(parent, generator.createProperty(name, raw), false)
        }
    }

    private fun remove(root: JsonObject, path: List<String>) {
        var parent = root
        for (key in path.dropLast(1)) parent = parent.child(key) ?: return
        parent.findProperty(path.last())?.let(::deleteProperty)
    }

    private fun JsonObject.child(key: String): JsonObject? = findProperty(key)?.value as? JsonObject

    private fun childObject(generator: JsonElementGenerator, parent: JsonObject, key: String): JsonObject? {
        parent.child(key)?.let { return it }
        val added = JsonPsiUtil.addProperty(parent, generator.createProperty(key, "{}"), false)
        return (added as? JsonProperty)?.value as? JsonObject
    }

    /**
     * Removes a property together with the separator that would otherwise be left dangling, and
     * with the whitespace that would otherwise be left as a blank line.
     */
    private fun deleteProperty(property: JsonProperty) {
        val following = PsiTreeUtil.skipWhitespacesAndCommentsForward(property)
        val preceding = PsiTreeUtil.skipWhitespacesAndCommentsBackward(property)
        when {
            following?.textMatches(",") == true -> {
                (property.prevSibling as? PsiWhiteSpace)?.delete()
                following.delete()
            }
            preceding?.textMatches(",") == true -> {
                (preceding.nextSibling as? PsiWhiteSpace)?.delete()
                preceding.delete()
            }
        }
        property.delete()
    }
}
