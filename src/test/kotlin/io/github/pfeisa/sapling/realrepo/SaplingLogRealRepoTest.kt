package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.log.activeBookmarkFrom
import io.github.pfeisa.sapling.log.parseSaplingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogRealRepoTest {

    @Test
    fun parsesLogAcrossTwoCommits() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "one\n"); repo.sl("add", "a.txt")
            repo.sl("commit", "-m", "first")
            repo.writeFile("a.txt", "one\ntwo\n")
            repo.sl("commit", "-m", "second")

            val res = repo.sl("log", "-Tjson")
            assertTrue("sl log failed: ${res.stderr}", res.success)
            val entries = parseSaplingLog(res.stdout)

            // Newest first: second, first, seed.
            assertTrue("at least 3 commits", entries.size >= 3)
            assertEquals("second", entries[0].description.trim())
            assertEquals("first", entries[1].description.trim())
            assertEquals(repo.node("."), entries[0].node)
            assertEquals("Sapling IT <it@example.com>", entries[0].author)
            assertTrue("epoch parsed as a positive Long", entries[0].dateEpochSeconds > 0)
            // bookmarks field parses against real output (none created => empty list, not null)
            assertTrue("no bookmarks by default", entries[0].bookmarks.isEmpty())
            // second's parent is first
            assertEquals(entries[1].node, entries[0].parents.single())
        }
    }

    /**
     * Validates the assumption behind [activeBookmarkFrom] (which feeds the cached,
     * EDT-safe getCurrentBranch): real `sl bookmark -Tjson` sets `active:true` on the
     * active bookmark, and no bookmark is active when none has been created.
     */
    @Test
    fun deriveActiveBookmarkFromRealBookmarkJson() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            // No bookmarks yet → nothing active.
            val none = repo.sl("bookmark", "-Tjson")
            assertTrue("sl bookmark failed: ${none.stderr}", none.success)
            assertNull("no active bookmark on a fresh repo", activeBookmarkFrom(none.stdout))

            // `sl bookmark <name>` creates AND activates it.
            assertTrue(repo.sl("bookmark", "feature").success)
            val active = repo.sl("bookmark", "-Tjson")
            assertTrue("sl bookmark failed: ${active.stderr}", active.success)
            assertEquals("feature", activeBookmarkFrom(active.stdout))
        }
    }
}
