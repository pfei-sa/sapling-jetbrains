package io.github.pfeisa.sapling.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogParserTest {

    @Test
    fun parsesEntries() {
        val json = """
            [
             {"rev":4,"node":"a765632148dc","user":"Ann <a@x>","date":[1700000000,0],
              "desc":"second\ncommit","parents":["b111"],"bookmarks":["main"]},
             {"rev":3,"node":"b111","user":"Bo","date":[1699990000,0],"desc":"first","parents":[]}
            ]
        """.trimIndent()

        val entries = parseSaplingLog(json)

        assertEquals(2, entries.size)
        assertEquals("a765632148dc", entries[0].node)
        assertEquals("Ann <a@x>", entries[0].author)
        assertEquals(1700000000L, entries[0].dateEpochSeconds)
        assertEquals("second\ncommit", entries[0].description)
        assertEquals(listOf("b111"), entries[0].parents)
        assertEquals(listOf("main"), entries[0].bookmarks)
        assertTrue(entries[1].parents.isEmpty())
    }

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(parseSaplingLog("").isEmpty())
        assertTrue(parseSaplingLog("[]").isEmpty())
    }
}
