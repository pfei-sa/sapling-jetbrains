package io.github.pfeisa.sapling.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogStreamTest {

    private val NUL = '\u0000'
    private val NULL_NODE = "0".repeat(40)

    @Test fun parsesNonMergeCommit() {
        val line = "node1${NUL}parent1${NUL}$NULL_NODE${NUL}1783925412 25200${NUL}Peng Fei <p@e.com>"
        val c = parseStreamedCommitLine(line)!!
        assertEquals("node1", c.node)
        assertEquals(listOf("parent1"), c.parents)
        assertEquals(1783925412000L, c.timestampMs)
        assertEquals("Peng Fei <p@e.com>", c.author)
    }

    @Test fun parsesRootCommitWithNoParents() {
        val line = "root1${NUL}$NULL_NODE${NUL}$NULL_NODE${NUL}1783919513 25200${NUL}A <a@e.com>"
        val c = parseStreamedCommitLine(line)!!
        assertEquals("root1", c.node)
        assertTrue("root has no parents", c.parents.isEmpty())
    }

    @Test fun parsesMergeCommitWithTwoParents() {
        val line = "m1${NUL}p1${NUL}p2${NUL}1783919513 0${NUL}A <a@e.com>"
        val c = parseStreamedCommitLine(line)!!
        assertEquals(listOf("p1", "p2"), c.parents)
    }

    @Test fun rejectsBlankAndMalformedLines() {
        assertNull(parseStreamedCommitLine(""))
        assertNull(parseStreamedCommitLine("   "))
        assertNull(parseStreamedCommitLine("only${NUL}three${NUL}fields"))
    }
}
