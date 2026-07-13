package io.github.pfeisa.sapling.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hermetic tests for [activeBookmarkFrom] — the pure derivation that feeds the
 * cached [SaplingLogProvider.getCurrentBranch]. getCurrentBranch is called by the
 * platform on the EDT, so it must return a cached value derived off-EDT rather than
 * spawn `sl`; this covers the derivation logic. (The EDT-safety itself is structural
 * — a cache lookup cannot spawn a process — and is validated via runIde.)
 */
class SaplingActiveBookmarkTest {

    @Test
    fun returnsActiveBookmarkName() {
        val json = """
            [ {"active": false, "bookmark": "main",    "node": "aaa"},
              {"active": true,  "bookmark": "feature", "node": "bbb"} ]
        """.trimIndent()
        assertEquals("feature", activeBookmarkFrom(json))
    }

    @Test
    fun nullWhenNoBookmarkIsActive() {
        val json = """[ {"active": false, "bookmark": "main", "node": "aaa"} ]"""
        assertNull(activeBookmarkFrom(json))
    }

    @Test
    fun nullWhenThereAreNoBookmarks() {
        assertNull(activeBookmarkFrom("[]"))
        assertNull(activeBookmarkFrom(""))
    }

    @Test
    fun nullWhenActiveBookmarkNameIsBlank() {
        val json = """[ {"active": true, "bookmark": "", "node": "aaa"} ]"""
        assertNull(activeBookmarkFrom(json))
    }
}
