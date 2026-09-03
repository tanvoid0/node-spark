package com.nodespark.lint

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.TextRange

/** Which parts of a file prettier would rewrite. Whole-file text in, editor ranges out. */
object PrettierDiff {

    /**
     * Original-side line ranges (start inclusive, end exclusive) that differ from prettier's
     * output. The platform's own line diff does the work — this is the same comparison the diff
     * viewer shows, so the highlighted regions match what "Reformat with Prettier" then changes.
     */
    fun changedLines(original: String, formatted: String): List<Pair<Int, Int>> =
        ComparisonManager.getInstance()
            .compareLines(original, formatted, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
            .map { it.startLine1 to it.endLine1 }

    /**
     * A half-open original-side line range as a document range, clamped to the document as it is
     * NOW — it may have moved on since the text was snapshotted for the prettier process.
     * An empty range (prettier inserting lines) is reported on the line it would insert before.
     */
    fun toRange(startLine: Int, endLine: Int, lineStarts: IntArray, textLength: Int): TextRange? {
        if (lineStarts.isEmpty() || textLength <= 0) return null
        val first = startLine.coerceIn(0, lineStarts.size - 1)
        val start = lineStarts[first].coerceIn(0, textLength)
        val last = endLine.coerceIn(first + 1, lineStarts.size)
        // lineStarts[last] is the offset just past that line's newline; back off it so the
        // highlight stops at the end of the last changed line instead of bleeding into the next.
        val end = (if (last < lineStarts.size) lineStarts[last] - 1 else textLength).coerceAtMost(textLength)
        if (end > start) return TextRange(start, end)
        // A blank or last-position line: highlight one character so the annotation is visible.
        return if (start > 0) TextRange(start - 1, start) else null
    }
}
