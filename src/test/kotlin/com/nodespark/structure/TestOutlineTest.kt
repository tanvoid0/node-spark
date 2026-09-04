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

    @Test
    fun `pathAt gives the describe chain enclosing an offset`() {
        val text = """
            describe('UserService', () => {
              describe('login', () => {
                it('returns a token', () => {
                  const x = 1;
                });
              });
            });

            describe('other', () => {
              it('unrelated', () => {});
            });
        """.trimIndent()

        val inside = text.indexOf("const x = 1")
        assertEquals(
            listOf("UserService", "login", "returns a token"),
            TestOutline.pathAt(text, inside).map { it.name },
        )

        val onSecondRoot = text.indexOf("it('unrelated'")
        assertEquals(
            listOf("other", "unrelated"),
            TestOutline.pathAt(text, onSecondRoot).map { it.name },
        )

        // Between the two roots: outside every block, so no chain at all.
        val between = text.indexOf("describe('other'") - 1
        assertEquals(emptyList<String>(), TestOutline.pathAt(text, between).map { it.name })
    }

    @Test
    fun `pathAt is bounded by the call, not by the first brace`() {
        // An expression-bodied callback opens no brace at all, and an options object opens one that
        // is not the body — both used to swallow everything after the test.
        val text = """
            describe('s', () => {
              it('a', () => expect(1).toBe(1));
              const helper = 1;
              test('b', { concurrent: true }, () => {
                const z = 1;
              });
            });
        """.trimIndent()

        assertEquals(
            listOf("s"),
            TestOutline.pathAt(text, text.indexOf("const helper")).map { it.name },
        )
        assertEquals(
            listOf("s", "b"),
            TestOutline.pathAt(text, text.indexOf("const z")).map { it.name },
        )
        assertEquals(
            listOf("s", "a"),
            TestOutline.pathAt(text, text.indexOf("expect(1)")).map { it.name },
        )
    }
}
