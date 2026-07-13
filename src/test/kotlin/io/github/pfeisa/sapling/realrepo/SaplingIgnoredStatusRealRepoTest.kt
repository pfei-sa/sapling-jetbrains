package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.parseSaplingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the real-`sl` behavior the ignored-file greying relies on: `sl status -i --terse=i` exists
 * (a hidden flag) and collapses a fully-ignored directory to a single trailing-slash entry instead of
 * enumerating every file under it. See `SaplingChangeProvider.reportIgnoredFiles`.
 */
class SaplingIgnoredStatusRealRepoTest {

    @Test
    fun terseCollapsesFullyIgnoredDirectory() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile(".gitignore", "ignored_dir/\n*.tmp\n")
            repo.sl("add", ".gitignore")
            repo.sl("commit", "-m", "add gitignore")

            repo.writeFile("ignored_dir/a.txt", "x\n")
            repo.writeFile("ignored_dir/nested/b.txt", "y\n")
            repo.writeFile("scratch.tmp", "z\n")

            val res = repo.sl("status", "-i", "--terse=i", "-Tjson")
            assertTrue("sl status -i --terse=i failed: ${res.stderr}", res.success)

            val ignored = parseSaplingStatus(res.stdout)
                .filter { it.status == SaplingStatusCode.IGNORED }
                .map { it.path }
                .toSet()

            // The whole directory collapses to a single trailing-slash entry.
            assertTrue("expected collapsed 'ignored_dir/'; got $ignored", "ignored_dir/" in ignored)
            // …and is NOT enumerated file-by-file.
            assertFalse(
                "directory should collapse, not enumerate; got $ignored",
                ignored.any { it != "ignored_dir/" && it.startsWith("ignored_dir/") },
            )
            // A lone ignored file appears as a plain (no trailing slash) entry.
            assertTrue("expected 'scratch.tmp'; got $ignored", "scratch.tmp" in ignored)
        }
    }
}
