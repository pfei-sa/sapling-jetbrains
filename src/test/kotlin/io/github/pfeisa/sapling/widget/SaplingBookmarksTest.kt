package io.github.pfeisa.sapling.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingBookmarksTest {
    @Test
    fun parsesActiveAndInactiveBookmarks() {
        val json = """
            [ {"active": false, "bookmark": "main",    "node": "aaa"},
              {"active": true,  "bookmark": "feature", "node": "bbb"} ]
        """.trimIndent()
        val result = parseBookmarkList(json)
        assertEquals(2, result.size)
        assertEquals(SaplingBookmark("main", "aaa", false), result[0])
        assertEquals(SaplingBookmark("feature", "bbb", true), result[1])
    }

    @Test
    fun dropsBlankNamedEntries() {
        val json = """[ {"active": true, "bookmark": "", "node": "aaa"} ]"""
        assertTrue(parseBookmarkList(json).isEmpty())
    }

    @Test
    fun emptyForBlankOrMalformedJson() {
        assertTrue(parseBookmarkList("").isEmpty())
        assertTrue(parseBookmarkList("not json").isEmpty())
    }
}
