package com.nodespark.lint

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** One entry from `eslint --format json`. All line/column numbers are 1-based; endColumn is exclusive. */
data class EslintMessage(
    val ruleId: String?,      // null on fatal parse errors
    val severity: Int,        // 1 = warning, 2 = error
    val message: String,
    val line: Int,
    val column: Int,
    val endLine: Int?,
    val endColumn: Int?,
)

/** Pure: no IntelliJ types, so both halves are directly unit-testable. */
object EslintOutputParser {

    /** `eslint --format json` is an array of file results, each with a `messages` array. */
    fun parse(json: String): List<EslintMessage> = try {
        val root = Gson().fromJson(json, JsonArray::class.java)
        root?.mapNotNull { it as? JsonObject }
            ?.flatMap { file -> (file.get("messages") as? JsonArray)?.toList().orEmpty() }
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { m ->
                val text = m.str("message") ?: return@mapNotNull null
                EslintMessage(
                    ruleId = m.str("ruleId"),
                    severity = m.int("severity") ?: 1,
                    message = text,
                    line = m.int("line") ?: 1,
                    column = m.int("column") ?: 1,
                    endLine = m.int("endLine"),
                    endColumn = m.int("endColumn"),
                )
            }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()  // malformed JSON, wrong shape, stderr noise on stdout — stay quiet
    }

    /**
     * 1-based line/column -> document offsets, given each line's start offset.
     * Returns start..endExclusive, or null when the message points outside the current text.
     */
    fun toOffsets(m: EslintMessage, lineStarts: IntArray, textLength: Int): IntRange? {
        val li = m.line - 1
        if (li !in lineStarts.indices) return null
        val start = (lineStarts[li] + (m.column - 1)).coerceIn(0, textLength)
        if (start >= textLength) return null

        val ei = m.endLine?.minus(1)
        var end = if (ei != null && m.endColumn != null && ei in lineStarts.indices) {
            (lineStarts[ei] + (m.endColumn - 1)).coerceIn(0, textLength)
        } else {
            start + 1
        }
        if (end <= start) end = start + 1
        return start..minOf(end, textLength)
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt
}
