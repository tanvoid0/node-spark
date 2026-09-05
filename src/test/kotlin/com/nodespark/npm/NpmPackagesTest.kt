package com.nodespark.npm

import com.nodespark.npm.NpmPackages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NpmPackagesTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun pkg(relative: String) {
        val file = File(tmp.root, "$relative/package.json")
        file.parentFile.mkdirs()
        file.writeText("{}")
    }

    @Test fun `finds workspace packages and skips node_modules, build output and dot dirs`() {
        pkg("")
        pkg("packages/api")
        pkg("packages/web")
        pkg("node_modules/left-pad")
        pkg("dist")
        pkg(".cache/thing")

        assertEquals(
            listOf(tmp.root, File(tmp.root, "packages/api"), File(tmp.root, "packages/web")),
            NpmPackages.find(tmp.root).map { it.parentFile },
        )
    }

    @Test fun `stops at the depth limit`() {
        pkg("a/b/c")
        assertEquals(emptyList<File>(), NpmPackages.find(tmp.root, maxDepth = 2))
        assertEquals(1, NpmPackages.find(tmp.root, maxDepth = 3).size)
    }
}
