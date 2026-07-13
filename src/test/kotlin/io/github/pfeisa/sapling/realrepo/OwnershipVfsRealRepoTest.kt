package io.github.pfeisa.sapling.realrepo

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.detection.SaplingVcsRootChecker

class OwnershipVfsRealRepoTest : BasePlatformTestCase() {

    fun testAutoClaimsRealSlRootButNotRealGitRoot() {
        if (!IntegrationTools.slAvailable || !IntegrationTools.gitAvailable) return
        SlTestRepo.create().use { sl ->
            GitTestRepo.create().use { git ->
                val lfs = LocalFileSystem.getInstance()
                val slVf = lfs.refreshAndFindFileByNioFile(sl.root)
                val gitVf = lfs.refreshAndFindFileByNioFile(git.root)
                assertNotNull(slVf); assertNotNull(gitVf)
                val checker = SaplingVcsRootChecker()
                assertTrue("claims real .sl root", checker.isRoot(slVf!!))
                assertFalse("stays out of real .git root", checker.isRoot(gitVf!!))
            }
        }
    }
}
