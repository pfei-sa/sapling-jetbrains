package io.github.pfeisa.sapling.detection

import io.github.pfeisa.sapling.SaplingVcs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isRoot(VirtualFile)` delegates to [SaplingRepoDetector.isSaplingRoot], whose
 * filesystem detection is covered directly by SaplingRepoDetectorTest; here we
 * verify the checker's VCS identity and admin-dir recognition (no VFS needed).
 */
class SaplingVcsRootCheckerTest {

    private val checker = SaplingVcsRootChecker()

    @Test
    fun recognizesOnlyDotSlAdminDir() {
        assertTrue(checker.isVcsDir(".sl"))
        assertFalse(checker.isVcsDir(".git")) // dotgit defaults to Git4Idea
        assertFalse(checker.isVcsDir(".hg"))
    }

    @Test
    fun reportsSaplingAsSupportedVcs() {
        assertEquals(SaplingVcs.KEY, checker.getSupportedVcs())
    }
}
