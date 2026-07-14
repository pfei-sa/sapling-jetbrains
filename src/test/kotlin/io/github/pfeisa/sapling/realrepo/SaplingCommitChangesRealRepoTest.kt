package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.changes.suppressRenameSources
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.parseSaplingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingCommitChangesRealRepoTest {

    @Test
    fun changeSetForAModifyCommit() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("README.md", "seed\nmore\n")
            repo.sl("commit", "-m", "edit readme")
            val node = repo.node(".")

            val res = repo.sl("status", "--change", node, "-Tjson", "--copies")
            assertTrue("sl status --change failed: ${res.stderr}", res.success)
            val entries = parseSaplingStatus(res.stdout)
            val readme = entries.single { it.path == "README.md" }
            assertEquals(SaplingStatusCode.MODIFIED, readme.status)
        }
    }

    @Test
    fun renameCommitCollapsesToOneMovedEntry() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            // seed commit already has README.md; rename it.
            repo.sl("mv", "README.md", "DOCS.md")
            repo.sl("commit", "-m", "rename readme")
            val node = repo.node(".")

            val res = repo.sl("status", "--change", node, "-Tjson", "--copies")
            assertTrue("sl status --change failed: ${res.stderr}", res.success)
            val entries = suppressRenameSources(parseSaplingStatus(res.stdout))

            // The R half of the rename is suppressed; the A half carries the copy source.
            val added = entries.single { it.status == SaplingStatusCode.ADDED }
            assertEquals("DOCS.md", added.path)
            assertEquals("README.md", added.copySource)
            assertTrue("no standalone R entry survives", entries.none { it.status == SaplingStatusCode.REMOVED })
        }
    }

    @Test
    fun rootCommitReportsEverythingAdded() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            val root = repo.sl("log", "-r", "roots(::.)", "-T", "{node}\n").stdout.trim().lines().first()

            val res = repo.sl("status", "--change", root, "-Tjson", "--copies")
            assertTrue("sl status --change failed: ${res.stderr}", res.success)
            val entries = parseSaplingStatus(res.stdout)
            assertTrue("root has at least the seed file", entries.isNotEmpty())
            assertTrue("every root entry is ADDED", entries.all { it.status == SaplingStatusCode.ADDED })
        }
    }
}
