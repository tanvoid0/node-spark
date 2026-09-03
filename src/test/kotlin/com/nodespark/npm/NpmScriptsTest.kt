package com.nodespark.npm

import org.junit.Assert.*
import org.junit.Test

class NpmScriptsTest {

    @Test fun `extracts scripts`() {
        val json = """
            { "name": "demo", "version": "1.0.0",
              "scripts": { "build": "tsc", "test": "jest --ci" },
              "devDependencies": { "jest": "^29" } }
        """.trimIndent()
        assertEquals(mapOf("build" to "tsc", "test" to "jest --ci"), NpmScripts.scriptsOf(json))
    }

    @Test fun `preserves declaration order`() {
        val json = """{"scripts":{"z":"1","a":"2","m":"3"}}"""
        assertEquals(listOf("z", "a", "m"), NpmScripts.scriptsOf(json).keys.toList())
    }

    @Test fun `no scripts key is empty`() {
        assertTrue(NpmScripts.scriptsOf("""{"name":"demo"}""").isEmpty())
    }

    @Test fun `nested scripts key is not picked up`() {
        assertTrue(NpmScripts.scriptsOf("""{"config":{"scripts":{"build":"nope"}}}""").isEmpty())
    }

    @Test fun `malformed json does not throw`() {
        assertTrue(NpmScripts.scriptsOf("""{"scripts": {"build": ""}""").isEmpty())
        assertTrue(NpmScripts.scriptsOf("").isEmpty())
        assertTrue(NpmScripts.scriptsOf("not json at all").isEmpty())
    }

    @Test fun `non-object scripts value does not throw`() {
        assertTrue(NpmScripts.scriptsOf("""{"scripts": "build"}""").isEmpty())
    }
}
