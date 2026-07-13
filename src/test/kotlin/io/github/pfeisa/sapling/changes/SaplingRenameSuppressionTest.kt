package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingRenameSuppressionTest {

    private fun entry(path: String, status: SaplingStatusCode, copy: String? = null) =
        SaplingStatusEntry(path, status, copy)

    /** `sl mv old new` -> `A new (copy=old)` + `R old`; the redundant `R old` must be dropped. */
    @Test
    fun dropsRemovedEntryThatIsACopySource() {
        val entries = listOf(
            entry("new", SaplingStatusCode.ADDED, copy = "old"),
            entry("old", SaplingStatusCode.REMOVED),
        )
        val result = suppressRenameSources(entries)
        assertEquals(listOf(entry("new", SaplingStatusCode.ADDED, copy = "old")), result)
    }

    /** A plain `sl copy` (no matching `R`) leaves the list untouched. */
    @Test
    fun keepsPlainCopyUntouched() {
        val entries = listOf(
            entry("copied", SaplingStatusCode.ADDED, copy = "original"),
            entry("original", SaplingStatusCode.MODIFIED),
        )
        assertEquals(entries, suppressRenameSources(entries))
    }

    /** An unrelated deletion (not a copy-source) is preserved even alongside a rename. */
    @Test
    fun keepsUnrelatedRemoval() {
        val entries = listOf(
            entry("new", SaplingStatusCode.ADDED, copy = "old"),
            entry("old", SaplingStatusCode.REMOVED),
            entry("unrelated", SaplingStatusCode.REMOVED),
        )
        val result = suppressRenameSources(entries)
        assertTrue(result.any { it.path == "unrelated" && it.status == SaplingStatusCode.REMOVED })
        assertTrue(result.none { it.path == "old" })
        assertEquals(2, result.size)
    }

    /** No copies at all -> identity (and the same list is returned). */
    @Test
    fun noCopiesIsIdentity() {
        val entries = listOf(
            entry("a", SaplingStatusCode.MODIFIED),
            entry("b", SaplingStatusCode.REMOVED),
        )
        assertEquals(entries, suppressRenameSources(entries))
    }
}
