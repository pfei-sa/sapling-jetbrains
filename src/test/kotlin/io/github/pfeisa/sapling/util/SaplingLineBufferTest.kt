package io.github.pfeisa.sapling.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingLineBufferTest {

    @Test fun splitsCompleteLinesAndKeepsRemainder() {
        val b = SaplingLineBuffer()
        assertEquals(listOf("a", "b"), b.append("a\nb\nc"))
        assertEquals("c", b.remainder())
    }

    @Test fun reassemblesLineSplitAcrossChunks() {
        val b = SaplingLineBuffer()
        assertEquals(emptyList<String>(), b.append("hel"))
        assertEquals(listOf("hello"), b.append("lo\n"))
        assertEquals("", b.remainder())
    }

    @Test fun trailingNewlineLeavesEmptyRemainder() {
        val b = SaplingLineBuffer()
        assertEquals(listOf("x"), b.append("x\n"))
        assertEquals("", b.remainder())
    }

    @Test fun emptyInputYieldsNothing() {
        val b = SaplingLineBuffer()
        assertEquals(emptyList<String>(), b.append(""))
        assertEquals("", b.remainder())
    }
}
