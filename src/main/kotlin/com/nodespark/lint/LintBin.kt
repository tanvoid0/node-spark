package com.nodespark.lint

import java.io.File

/**
 * The project-local node_modules/.bin/<name>, or null when the tool is not installed.
 * Deliberately NOT NodeCommandLine.resolveBin: that falls back to a PATH lookup, which would run a
 * globally-installed eslint against a project that has none. Null here means "do nothing at all".
 */
fun localBin(workDir: String, name: String): String? =
    File(workDir, "node_modules/.bin/$name.cmd").takeIf { it.isFile }?.absolutePath
        ?: File(workDir, "node_modules/.bin/$name").takeIf { it.isFile }?.absolutePath
