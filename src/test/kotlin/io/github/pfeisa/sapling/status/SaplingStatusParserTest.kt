package io.github.pfeisa.sapling.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingStatusParserTest {

    @Test
    fun parsesStatusWithCopies() {
        val json = """
            [
             { "path": "added",    "status": "A" },
             { "path": "copied",   "status": "A", "copy": "modified" },
             { "path": "modified", "status": "M" },
             { "path": "removed",  "status": "R" }
            ]
        """.trimIndent()

        val entries = parseSaplingStatus(json)

        assertEquals(4, entries.size)
        assertEquals(SaplingStatusEntry("added", SaplingStatusCode.ADDED, null), entries[0])
        assertEquals("modified", entries[1].copySource)
        assertEquals(SaplingStatusCode.ADDED, entries[1].status)
        assertEquals(SaplingStatusCode.MODIFIED, entries[2].status)
        assertEquals(SaplingStatusCode.REMOVED, entries[3].status)
    }

    @Test
    fun preservesIgnoredDirectoryEntriesVerbatim() {
        // `sl status -i --terse=i` collapses fully-ignored dirs to a single `I` entry with a trailing slash.
        val json = """
            [
             { "path": "build/",           "status": "I" },
             { "path": "docs/internal/",   "status": "I" },
             { "path": "scratch.tmp",      "status": "I" }
            ]
        """.trimIndent()

        val entries = parseSaplingStatus(json)

        assertEquals(3, entries.size)
        assertTrue(entries.all { it.status == SaplingStatusCode.IGNORED })
        // The trailing slash is retained verbatim so the consumer can derive the directory flag.
        assertEquals("build/", entries[0].path)
        assertEquals("docs/internal/", entries[1].path)
        assertEquals("scratch.tmp", entries[2].path)
    }

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(parseSaplingStatus("").isEmpty())
        assertTrue(parseSaplingStatus("[]").isEmpty())
    }

    @Test
    fun unknownStatusCodeIsSkipped() {
        val entries = parseSaplingStatus("""[ { "path": "x", "status": "Z" } ]""")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun fromCodeMapsKnownAndRejectsUnknown() {
        assertEquals(SaplingStatusCode.MISSING, SaplingStatusCode.fromCode("!"))
        assertNull(SaplingStatusCode.fromCode("Z"))
    }
}
