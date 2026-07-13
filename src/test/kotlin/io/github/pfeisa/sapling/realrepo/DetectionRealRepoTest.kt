package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import io.github.pfeisa.sapling.detection.SaplingVcsRootChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DetectionRealRepoTest {

    @Test
    fun detectsSlRootFromRootAndNestedSubdir() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            assertTrue(SaplingRepoDetector.isSaplingRoot(repo.root))
            val nested = repo.root.resolve("a/b/c")
            Files.createDirectories(nested)
            assertEquals(repo.root, SaplingRepoDetector.findRepoRoot(nested))
        }
    }

    @Test
    fun plainGitRepoIsNeverClaimedEvenThoughSlCanOperateOnIt() {
        IntegrationTools.assumeSlAndGit()
        GitTestRepo.create().use { repo ->
            // Finding: `sl root` returns 0 in a git-compat repo. The plugin's non-claim guarantee
            // therefore rests on the `.sl`-only detector, NOT on `sl root` failing. Run it to
            // document the behavior, then assert the guarantee holds regardless of sl's outcome.
            IntegrationTools.exec(listOf("sl", "root"), repo.root, emptyMap())
            assertFalse("no .sl dir was created", Files.isDirectory(repo.root.resolve(".sl")))
            assertFalse(SaplingRepoDetector.isSaplingRoot(repo.root))
            assertNull(SaplingRepoDetector.findRepoRoot(repo.root))
            val checker = SaplingVcsRootChecker()
            assertFalse("does not treat .git as a Sapling dir", checker.isVcsDir(".git"))
            assertTrue("does treat .sl as a Sapling dir", checker.isVcsDir(".sl"))
        }
    }
}
