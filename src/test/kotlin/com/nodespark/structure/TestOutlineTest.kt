package com.nodespark.structure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestOutlineTest {

    private val source = """
        const helper = require('./helper');

        // describe('commented out', () => {});
        /* describe('block commented', () => {}); */
        const label = `describe('in a template', () => {})`;

        describe('outer', () => {
          it('does a thing', () => {
            expect(1).toBe(1);
          });

          describe.skip('nested group', function () {
            it.only('focused', async () => {});
            test.todo('later');
          });

          it('one liner', () => expect(true).toBe(true));
        });

        describe('second root', () => {
          test.each([[1, 2], [3, 4]])('adds %i and %i', (a, b) => {
            expect(a).toBeLessThan(b);
          });
        });
    """.trimIndent()

    private val roots = TestOutline.parse(source)

    @Test fun `only real top-level describes become roots`() {
        assertEquals(listOf("outer", "second root"), roots.map { it.name })
    }

    @Test fun `comments and template literals are not parsed`() {
        // 'commented out', 'block commented' and 'in a template' must not appear anywhere
        val all = flatten(roots).map { it.name }
        assertTrue(all.none { it.contains("comment") || it.contains("template") })
    }

    @Test fun `children nest by brace depth`() {
        assertEquals(
            listOf("does a thing", "nested group", "one liner"),
            roots[0].children.map { it.name },
        )
        assertEquals(
            listOf("focused", "later"),
            roots[0].children[1].children.map { it.name },
        )
        assertEquals(listOf("adds %i and %i"), roots[1].children.map { it.name })
    }

    @Test fun `one-liner it is a sibling not a child`() {
        assertTrue(roots[0].children[2].children.isEmpty())
    }

    @Test fun `kinds are describe vs test`() {
        assertEquals(TestKind.DESCRIBE, roots[0].kind)
        assertEquals(TestKind.DESCRIBE, roots[0].children[1].kind)
        assertEquals(TestKind.TEST, roots[0].children[0].kind)
    }

    @Test fun `modifiers are picked off the chain`() {
        assertEquals(TestModifier.NONE, roots[0].modifier)
        assertEquals(TestModifier.SKIP, roots[0].children[1].modifier)
        assertEquals(TestModifier.ONLY, roots[0].children[1].children[0].modifier)
        assertEquals(TestModifier.TODO, roots[0].children[1].children[1].modifier)
        // .each is not a run modifier
        assertEquals(TestModifier.NONE, roots[1].children[0].modifier)
    }

    @Test fun `offset points at the keyword`() {
        assertTrue(source.startsWith("describe('outer'", roots[0].offset))
        assertTrue(source.startsWith("it.only('focused'", roots[0].children[1].children[0].offset))
    }

    @Test fun `each with a tagged template table`() {
        val nodes = TestOutline.parse(
            "test.each`\n  a | b\n  1 | 2\n`('adds \$a', () => {});"
        )
        assertEquals(listOf("adds \$a"), nodes.map { it.name })
    }

    @Test fun `double quoted and escaped names`() {
        val nodes = TestOutline.parse("it(\"say \\\"hi\\\"\", () => {});")
        assertEquals(listOf("say \"hi\""), nodes.map { it.name })
    }

    @Test fun `empty text yields nothing`() {
        assertEquals(emptyList<OutlineNode>(), TestOutline.parse(""))
    }

    private fun flatten(nodes: List<OutlineNode>): List<OutlineNode> =
        nodes.flatMap { listOf(it) + flatten(it.children) }
}
