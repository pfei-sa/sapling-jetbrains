package io.github.pfeisa.sapling.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingResolveConflictsParseTest {
    @Test
    fun keepsOnlyUnresolvedEntries() {
        val json = """
            [ {"path": "a.txt", "status": "U"},
              {"path": "b.txt", "status": "R"},
              {"path": "c.txt", "status": "U"} ]
        """.trimIndent()
        assertEquals(listOf("a.txt", "c.txt"), parseUnresolvedConflicts(json))
    }

    @Test
    fun emptyForBlankOrMalformed() {
        assertTrue(parseUnresolvedConflicts("").isEmpty())
        assertTrue(parseUnresolvedConflicts("nope").isEmpty())
    }
}
