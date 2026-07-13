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
