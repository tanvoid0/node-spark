package com.nodespark.lint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrettierDiffTest {

    // "abc\ndef\nghi" -> line starts 0, 4, 8; length 11
    private val lineStarts = intArrayOf(0, 4, 8)
    private val length = 11

    @Test
    fun `single line maps to that line without its newline`() {
        val r = PrettierDiff.toRange(1, 2, lineStarts, length)!!
        assertEquals(4, r.startOffset)
        assertEquals(7, r.endOffset)
    }

    @Test
    fun `multi line range spans to the end of the last line`() {
        val r = PrettierDiff.toRange(0, 2, lineStarts, length)!!
        assertEquals(0, r.startOffset)
        assertEquals(7, r.endOffset)
    }

    @Test
    fun `last line ends at the document end`() {
        val r = PrettierDiff.toRange(2, 3, lineStarts, length)!!
        assertEquals(8, r.startOffset)
        assertEquals(11, r.endOffset)
    }

    @Test
    fun `empty range - an insertion - still highlights something`() {
        val r = PrettierDiff.toRange(1, 1, lineStarts, length)!!
        assertEquals(4, r.startOffset)
        assertEquals(7, r.endOffset)
    }

    @Test
    fun `lines past the end of a shrunken document are clamped`() {
        val r = PrettierDiff.toRange(9, 12, lineStarts, length)!!
        assertEquals(8, r.startOffset)
        assertEquals(11, r.endOffset)
    }

    @Test
    fun `empty document yields nothing`() {
        assertNull(PrettierDiff.toRange(0, 1, intArrayOf(0), 0))
    }
}
