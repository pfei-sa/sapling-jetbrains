package io.github.pfeisa.sapling.blame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingBlameParserTest {

    @Test
    fun parsesLinesWithFloatDateAndIndexDerivedLineNumbers() {
        // Real `sl annotate -Tjson`: array of files, each with `lines`;
        // `date` epoch is a FLOAT; there is NO line_number field (index -> line number).
        val json = """
            [
              {
                "abspath": "a.txt",
                "path": "a.txt",
                "lines": [
                  {"line": "first\n",  "node": "abc123", "user": "Ann", "date": [1700000000.0, 0], "age_bucket": "1hour"},
                  {"line": "second\n", "node": "def456", "user": "Bo",  "date": [1700000100.0, 0], "age_bucket": "1hour"}
                ]
              }
            ]
        """.trimIndent()

        val lines = parseSaplingAnnotate(json)

        assertEquals(2, lines.size)
        assertEquals(1, lines[0].lineNumber)
        assertEquals("abc123", lines[0].node)
        assertEquals("Ann", lines[0].author)
        assertEquals(1700000000L, lines[0].dateEpochSeconds)
        assertEquals("first\n", lines[0].content)
        assertEquals(2, lines[1].lineNumber)
        assertEquals("def456", lines[1].node)
    }

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(parseSaplingAnnotate("").isEmpty())
        assertTrue(parseSaplingAnnotate("[]").isEmpty())
    }
}
