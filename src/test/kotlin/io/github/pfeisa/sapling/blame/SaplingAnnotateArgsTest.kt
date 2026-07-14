package io.github.pfeisa.sapling.blame

import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingAnnotateArgsTest {

    @Test
    fun annotateArgsWithoutRevision() {
        assertEquals(listOf("annotate", "-Tjson", "--", "a.txt"), buildAnnotateArgs(null, "a.txt"))
    }

    @Test
    fun annotateArgsWithRevisionInsertRevBeforeDashDash() {
        assertEquals(
            listOf("annotate", "-Tjson", "--rev", "abc123", "--", "a.txt"),
            buildAnnotateArgs("abc123", "a.txt"),
        )
    }

    @Test
    fun logArgsUnionRepeatedDashR() {
        assertEquals(
            listOf("log", "-Tjson", "-r", "n1", "-r", "n2"),
            buildLogArgs(listOf("n1", "n2")),
        )
    }
}
