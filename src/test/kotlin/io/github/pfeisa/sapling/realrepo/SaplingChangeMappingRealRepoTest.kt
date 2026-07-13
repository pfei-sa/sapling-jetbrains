package io.github.pfeisa.sapling.realrepo

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.changes.SaplingContentRevision
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.changes.statusEntryToChange
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.parseSaplingStatus

/** Drives the real SaplingCli + statusEntryToChange against a real repo, asserting Change objects. */
class SaplingChangeMappingRealRepoTest : BasePlatformTestCase() {

    fun testRealStatusYieldsModifiedAndRemovedChanges() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("mod.txt", "a\n"); repo.sl("add", "mod.txt")
            repo.writeFile("del.txt", "b\n"); repo.sl("add", "del.txt")
            repo.sl("commit", "-m", "base")
            repo.writeFile("mod.txt", "a\nchanged\n")
            repo.sl("remove", "del.txt")

            val cli = SaplingCli("sl")
            val res = cli.run(repo.root.toString(), listOf("status", "-Tjson", "--copies"))
            assertTrue("sl status failed: ${res.stderr}", res.success)
            val parentRev = SaplingRevisionNumber(repo.node("."))
            val changes = parseSaplingStatus(res.stdout)
                .mapNotNull { statusEntryToChange(it, repo.root, parentRev, cli) }
                .associateBy { (it.afterRevision ?: it.beforeRevision)!!.file.name }

            val mod = changes["mod.txt"]!!
            assertEquals(FileStatus.MODIFIED, mod.fileStatus)
            assertTrue(mod.beforeRevision is SaplingContentRevision)
            assertTrue(mod.afterRevision is CurrentContentRevision)

            val del = changes["del.txt"]!!
            assertEquals(FileStatus.DELETED, del.fileStatus)
            assertTrue(del.beforeRevision is SaplingContentRevision)
            assertNull(del.afterRevision)
        }
    }
}
