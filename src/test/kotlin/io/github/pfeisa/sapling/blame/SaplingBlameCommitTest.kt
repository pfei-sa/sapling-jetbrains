package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.log.SaplingLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingBlameCommitTest {

    private fun line(node: String, author: String = "AnnLine", date: Long = 1L) =
        SaplingBlameLine(lineNumber = 1, node = node, author = author, dateEpochSeconds = date, content = "x\n")

    private fun log(node: String, author: String, date: Long, desc: String) =
        SaplingLogEntry(node, author, date, desc, emptyList(), emptyList())

    @Test
    fun dedupesByNodeAndSortsByDateDescending() {
        val lines = listOf(line("aaa"), line("aaa"), line("bbb"))
        val logs = listOf(
            log("aaa", "Ann", 100, "older commit"),
            log("bbb", "Bo", 200, "newer commit"),
        )
        val commits = buildBlameCommits(lines, logs)

        assertEquals(2, commits.size)
        assertEquals("bbb", commits[0].node) // newer first
        assertEquals("Bo", commits[0].author)
        assertEquals("newer commit", commits[0].message)
        assertEquals("aaa", commits[1].node)
    }

    @Test
    fun degradesToAnnotateDataWhenLogEntryMissing() {
        // Call 2 (sl log) failed → empty logEntries. Author/date come from the annotate line; message empty.
        val commits = buildBlameCommits(listOf(line("aaa", author = "FromAnnotate", date = 42)), emptyList())

        assertEquals(1, commits.size)
        assertEquals("aaa", commits[0].node)
        assertEquals("FromAnnotate", commits[0].author)
        assertEquals(42L, commits[0].dateEpochSeconds)
        assertEquals("", commits[0].message)
    }

    @Test
    fun skipsBlankNodes() {
        assertEquals(0, buildBlameCommits(listOf(line("")), emptyList()).size)
    }
}
