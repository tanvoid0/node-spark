package com.nodespark.env

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/** Locating the other .env files that sit beside the one being edited. */
object EnvSiblings {

    /** Names conventionally used for the committed, value-less template. */
    val TEMPLATE_NAMES = listOf(".env.example", ".env.sample", ".env.template", ".env.dist", "env.example")

    private const val MAX_BYTES = 512L * 1024

    fun isTemplate(name: String): Boolean = TEMPLATE_NAMES.any { name.equals(it, ignoreCase = true) }

    /** Every .env* file in the same directory, excluding [file] itself. */
    fun siblings(file: VirtualFile): List<VirtualFile> =
        file.parent?.children.orEmpty()
            .filter { !it.isDirectory && it != file && EnvFileType.matches(it) }
            .sortedBy { it.name }

    /** [file]'s text, or null when it is unreadable or too large to bother with. */
    fun readSafely(file: VirtualFile): String? = try {
        if (file.length > MAX_BYTES) null else VfsUtilCore.loadText(file)
    } catch (e: Exception) {
        logger<EnvSiblings>().debug("cannot read ${file.name}", e)
        null
    }

    /** Values assigned to [key] in the sibling .env files next to [file], deduplicated. */
    fun valuesFor(key: String, file: VirtualFile): List<String> =
        siblings(file).flatMap { sibling ->
            readSafely(sibling)?.let { text ->
                EnvFile.parse(text).pairs.filter { it.key == key && it.value.isNotEmpty() }.map { it.value }
            }.orEmpty()
        }.distinct()

    /** The template next to [file], if there is one. */
    fun template(file: VirtualFile): VirtualFile? =
        if (isTemplate(file.name)) null else siblings(file).firstOrNull { isTemplate(it.name) }

    /** Keys present in [template] but missing from [target]. */
    fun missingKeys(templateText: String, targetText: String): List<EnvFile.Pair> {
        val have = EnvFile.parse(targetText).pairs.mapTo(HashSet()) { it.key }
        return EnvFile.parse(templateText).pairs.filter { it.key !in have }
    }

    /**
     * [targetText] with every key missing from [templateText] appended, under a header comment.
     * Existing lines and values are never touched.
     */
    fun fillFromTemplate(templateText: String, targetText: String, header: String? = null): String {
        // A pair's text already carries the comment block documenting it, so the description of a
        // key comes across with the key.
        val added = missingKeys(templateText, targetText).map { it.text }
        if (added.isEmpty()) return targetText
        val sep = EnvFile.parse(targetText).separator
        val sb = StringBuilder(targetText.trimEnd('\n', '\r'))
        if (sb.isNotEmpty()) sb.append(sep).append(sep)
        if (header != null) sb.append("# ").append(header).append(sep)
        sb.append(added.joinToString(sep))
        sb.append(sep)
        return sb.toString()
    }
}
