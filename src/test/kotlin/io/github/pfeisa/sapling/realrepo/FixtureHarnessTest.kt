package io.github.pfeisa.sapling.realrepo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Proves the fixtures + build gating work end-to-end. */
class FixtureHarnessTest {

    @Test
    fun slRepoHasSlDirIdentityAndSeedCommit() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            assertTrue("`.sl` dir exists", Files.isDirectory(repo.root.resolve(".sl")))
            assertEquals("current node is a 40-char hash", 40, repo.node().length)
            assertEquals("seed", repo.sl("log", "-r", ".", "-T", "{desc}").stdout.trim())
        }
    }

    @Test
    fun gitRepoHasGitDirAndSeedCommit() {
        IntegrationTools.assumeSlAndGit()
        GitTestRepo.create().use { repo ->
            assertTrue("`.git` dir exists", Files.isDirectory(repo.root.resolve(".git")))
            assertTrue("HEAD resolves", repo.git("rev-parse", "HEAD").success)
        }
    }
}
