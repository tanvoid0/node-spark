package com.nodespark.npm

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NpmRegistryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `reads the search response shape, and survives a result missing its fields`() {
        val hits = NpmRegistry.parseSearch(
            """
            {
              "objects": [
                { "package": { "name": "react", "version": "18.3.1", "description": "A library" } },
                { "package": { "name": "react-dom" } },
                { "package": { "version": "1.0.0" } }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NpmRegistry.Hit("react", "18.3.1", "A library"),
                NpmRegistry.Hit("react-dom", "", ""),
            ),
            hits,
        )
    }

    @Test fun `a broken response is no results rather than an exception`() {
        assertEquals(emptyList<NpmRegistry.Hit>(), NpmRegistry.parseSearch("<html>502</html>"))
    }

    @Test fun `a project's own npmrc chooses the registry`() {
        File(tmp.root, ".npmrc").writeText("//registry.example.com/:_authToken=x\nregistry = https://registry.example.com/\n")
        assertEquals("https://registry.example.com", NpmRegistry.registryFor(tmp.root))
    }

    @Test fun `no npmrc means the public registry`() {
        // The user's home .npmrc would also be consulted, so only assert the shape of the fallback.
        val registry = NpmRegistry.registryFor(tmp.root)
        assertEquals(true, registry.startsWith("http"))
    }
}
