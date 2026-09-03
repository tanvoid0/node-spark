package com.nodespark

import java.nio.file.Files

/**
 * kotlin.io.createTempDir is deprecated at ERROR level in the Kotlin stdlib the 262 platform ships,
 * so tests share this instead of each calling it.
 */
internal fun tempDir(): java.io.File = Files.createTempDirectory("nodespark").toFile()
