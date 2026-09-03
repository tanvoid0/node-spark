package com.nodespark.coverage

import java.io.File

/**
 * lcov.info reader. Pure Kotlin — no IntelliJ imports, so it unit-tests without a fixture.
 *
 * Records are `SF:<path>` … `DA:<line>,<hits>` … `end_of_record`; everything else
 * (LF/LH/BRDA/FN/FNDA/TN) is ignored. A record left open at EOF is still flushed.
 */
object LcovParser {

    /** path key (see [key]) -> 1-based line number -> hit count. */
    fun parse(text: String, baseDir: File): Map<String, Map<Int, Int>> {
        val result = HashMap<String, MutableMap<Int, Int>>()
        var current: MutableMap<Int, Int>? = null
        var currentKey: String? = null

        fun flush() {
            val k = currentKey
            val c = current
            if (k != null && c != null) {
                // same file may appear twice (merged reports) — sum rather than replace
                val existing = result.getOrPut(k) { HashMap() }
                c.forEach { (l, h) -> existing[l] = (existing[l] ?: 0) + h }
            }
            currentKey = null
            current = null
        }

        for (raw in text.split(Regex("\r?\n"))) {
            val line = raw.trim()
            when {
                line.startsWith("SF:") -> {
                    flush()
                    currentKey = key(line.substring(3).trim(), baseDir)
                    current = HashMap()
                }
                line.startsWith("DA:") -> {
                    val parts = line.substring(3).split(',')
                    if (parts.size >= 2) {
                        val n = parts[0].trim().toIntOrNull()
                        val hits = parts[1].trim().toIntOrNull()
                        // duplicate DA for the same line accumulates rather than overwrites
                        if (n != null && hits != null) current?.let { it[n] = (it[n] ?: 0) + hits }
                    }
                }
                line == "end_of_record" -> flush()
                else -> {} // LF/LH/BRDA/FN/FNDA/TN/blank
            }
        }
        flush()
        return result
    }

    /**
     * Canonical lookup key for a source path. Both the writer (SF: entries, possibly relative)
     * and the reader (VirtualFile.path) MUST go through this — it is the only place the two agree.
     */
    fun key(path: String, baseDir: File): String {
        val f = File(path).let { if (it.isAbsolute) it else File(baseDir, path) }
        val p = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath).replace('\\', '/')
        // ponytail: File.separatorChar as the case-fold test instead of SystemInfo, to keep this file IntelliJ-free
        return if (File.separatorChar == '\\') p.lowercase() else p
    }
}
