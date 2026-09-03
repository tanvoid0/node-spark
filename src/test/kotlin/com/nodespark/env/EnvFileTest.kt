package com.nodespark.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvFileTest {

    @Test fun `parses plain assignments`() {
        assertEquals(
            mapOf("A" to "1", "B" to "two"),
            EnvFile.toMap("A=1\nB=two\n"),
        )
    }

    @Test fun `strips quotes and export, keeps inline comment out of the value`() {
        val text = """
            export TOKEN="abc def"
            LITERAL='raw ${'$'}VALUE'
            PLAIN=hello # trailing note
            HASHED=a#b
        """.trimIndent()
        val map = EnvFile.toMap(text)
        assertEquals("abc def", map["TOKEN"])
        assertEquals("raw \$VALUE", map["LITERAL"])
        assertEquals("hello", map["PLAIN"])
        assertEquals("a#b", map["HASHED"])
        assertTrue(EnvFile.parse(text).pairs.first().exported)
    }

    @Test fun `escapes and multi-line values`() {
        assertEquals("a\nb", EnvFile.toMap("""K="a\nb"""")["K"])
        assertEquals("line1\nline2", EnvFile.toMap("K=\"line1\nline2\"\nNEXT=1")["K"])
        assertEquals("1", EnvFile.toMap("K=\"line1\nline2\"\nNEXT=1")["NEXT"])
    }

    @Test fun `comments and blank lines survive a round trip`() {
        val text = "# header\n\nA=1\n# note\nB=2\n"
        assertEquals(text, EnvFile.parse(text).render())
    }

    @Test fun `withPairs edits values and keeps the comments in place`() {
        val text = "# header\nA=1\n# note\nB=2\n"
        val edited = EnvFile.withPairs(
            text,
            listOf(
                EnvFile.Pair("A", "9", doc = "header"),
                EnvFile.Pair("B", "2", doc = "note"),
                EnvFile.Pair("C", "new value"),
            ),
        )
        assertEquals("# header\nA=9\n# note\nB=2\n\nC=\"new value\"", edited)
    }

    @Test fun `a comment block above a key belongs to that key`() {
        val text = "# App\n\n# What it is.\n# Where it comes from.\nAPI_URL=x\nPORT=1\n"
        val parsed = EnvFile.parse(text)
        assertEquals("What it is.\nWhere it comes from.", parsed.pairs.first().doc)
        assertEquals("", parsed.pairs[1].doc)
        // The blank line keeps the file header out of the first key.
        assertTrue(parsed.lines.any { it is EnvFile.Raw && it.text == "# App" })
        assertEquals(text, parsed.render())
    }

    @Test fun `a block comment can be edited, added and removed`() {
        val text = "# one\n# two\nA=1\n"
        val pair = EnvFile.parse(text).pairs.single()
        assertEquals("one\ntwo", pair.doc)
        assertEquals(text, EnvFile.withPairs(text, listOf(pair)))
        assertEquals(
            "# one\n# two\n# three\nA=1\n",
            EnvFile.withPairs(text, listOf(EnvFile.Pair("A", "1", doc = "one\ntwo\nthree"))),
        )
        assertEquals("A=1\n", EnvFile.withPairs(text, listOf(EnvFile.Pair("A", "1"))))
    }

    @Test fun `a block comment round trips through render`() {
        val rendered = EnvFile.render("K", "v", false, "inline", "first\nsecond")
        assertEquals("# first\n# second\nK=v  # inline", rendered)
        val pair = EnvFile.parse(rendered).pairs.single()
        assertEquals("first\nsecond", pair.doc)
        assertEquals("inline", pair.comment)
        assertEquals("v", pair.value)
    }

    @Test fun `a rewritten block comment follows the file separator`() {
        val text = "# one\r\nA=1\r\nB=2\r\n"
        val edited = EnvFile.withPairs(text, listOf(EnvFile.Pair("A", "9", doc = "one\ntwo"), EnvFile.Pair("B", "2")))
        assertEquals("# one\r\n# two\r\nA=9\r\nB=2\r\n", edited)
    }

    @Test fun `withPairs removes a deleted row`() {
        assertEquals("A=1", EnvFile.withPairs("A=1\nB=2", listOf(EnvFile.Pair("A", "1"))))
    }

    @Test fun `quoting only when needed`() {
        assertEquals("plain", EnvFile.quote("plain"))
        assertEquals("\"\"", EnvFile.quote(""))
        assertEquals("\"a b\"", EnvFile.quote("a b"))
        assertEquals("'\$HOME/bin'", EnvFile.quote("\$HOME/bin"))
        assertEquals("\"a\\nb\"", EnvFile.quote("a\nb"))
        // Whatever the quoting, the value must come back out unchanged.
        for (value in listOf("a b", "", "\$HOME", "a\nb", "quote\"inside", "hash # here", "it's")) {
            assertEquals(value, EnvFile.toMap("K=" + EnvFile.quote(value))["K"])
        }
    }

    @Test fun `crlf files stay crlf`() {
        val text = "A=1\r\nB=2\r\n"
        assertEquals(text, EnvFile.withPairs(text, listOf(EnvFile.Pair("A", "1"), EnvFile.Pair("B", "2"))))
    }

    @Test fun `duplicate keys are reported at their repeat`() {
        val text = "A=1\nB=2\nA=3\n"
        val dups = EnvFile.duplicateKeyRanges(text)
        assertEquals(1, dups.size)
        assertEquals("A", dups[0].first)
        assertEquals("A", text.substring(dups[0].second, dups[0].third))
        assertEquals(text.lastIndexOf("A=3"), dups[0].second)
    }

    @Test fun `non assignments are left alone`() {
        val text = "not a pair\n1BAD=x\nGOOD=y"
        assertEquals(mapOf("GOOD" to "y"), EnvFile.toMap(text))
        assertEquals(text, EnvFile.parse(text).render())
    }

    @Test fun `fill adds only missing keys`() {
        val template = "# app\nA=example\nB=example\n"
        val target = "A=mine\n"
        val filled = EnvSiblings.fillFromTemplate(template, target, "from .env.example")
        assertEquals("A=mine\n\n# from .env.example\nB=example\n", filled)
        assertEquals(emptyList<EnvFile.Pair>(), EnvSiblings.missingKeys(template, filled))
    }

    @Test fun `inline comments are parsed and survive a rewrite`() {
        val text = "A=1 # first\nB=\"two\" # second\nC=3\n"
        val pairs = EnvFile.parse(text).pairs
        assertEquals(listOf("first", "second", ""), pairs.map { it.comment })
        assertEquals(listOf("1", "two", "3"), pairs.map { it.value })
        // Editing one row must not strip the notes on the others.
        val edited = EnvFile.withPairs(text, pairs.map { if (it.key == "A") it.copy(value = "9", text = EnvFile.render(it.key, "9", it.exported, it.comment)) else it })
        // The edited row is re-rendered with its note; untouched rows keep their exact source text.
        assertEquals("A=9  # first\nB=\"two\" # second\nC=3\n", edited)
    }

    @Test fun `a comment cell can be added to a row that had none`() {
        val edited = EnvFile.withPairs("A=1\n", listOf(EnvFile.Pair("A", "1", false, "why")))
        assertEquals("A=1  # why\n", edited)
        assertEquals("why", EnvFile.parse(edited).pairs.single().comment)
        assertEquals("1", EnvFile.parse(edited).pairs.single().value)
    }

    @Test fun `fill carries the comment block above a key`() {
        val template = "# App\n\n# The public API root.\n# Include the scheme.\nAPI_URL=https://example.test\nPORT=3000\n"
        val filled = EnvSiblings.fillFromTemplate(template, "PORT=1234\n", "from .env.example")
        assertEquals(
            "PORT=1234\n\n# from .env.example\n# The public API root.\n# Include the scheme.\nAPI_URL=https://example.test\n",
            filled,
        )
    }

    @Test fun `fill on an empty file writes every key`() {
        assertEquals("# t\nA=1\nB=2\n", EnvSiblings.fillFromTemplate("A=1\nB=2\n", "", "t"))
    }

    @Test fun `template names`() {
        assertTrue(EnvSiblings.isTemplate(".env.example"))
        assertTrue(EnvSiblings.isTemplate(".ENV.Example"))
        assertTrue(!EnvSiblings.isTemplate(".env.local"))
    }

    @Test fun `file name matching`() {
        for (name in listOf(".env", ".env.local", ".env.production.local", "test.env")) {
            assertTrue(name, EnvFileType.matches(name))
        }
        for (name in listOf("env", "environment.ts", "readme.md")) {
            assertTrue(name, !EnvFileType.matches(name))
        }
    }
}
