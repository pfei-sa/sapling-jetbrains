package io.github.pfeisa.sapling.realrepo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.history.SaplingHistoryProvider

class SaplingHistoryRealRepoTest : BasePlatformTestCase() {

    /** Core behavior: per-file history across multiple commits of a stable (un-renamed) file. */
    fun testHistoryAcrossCommitsOfAStableFile() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "v1\n"); repo.sl("add", "a.txt")
            repo.sl("commit", "-m", "c1")
            repo.writeFile("a.txt", "v1\nv2\n")
            repo.sl("commit", "-m", "c2")
            repo.writeFile("a.txt", "v1\nv2\nv3\n")
            repo.sl("commit", "-m", "c3")

            val provider = SaplingHistoryProvider(project, SaplingCli("sl"))
            val filePath = VcsUtil.getFilePath(repo.root.resolve("a.txt").toFile(), false)
            val session = provider.createSessionFor(filePath)
            assertNotNull(session)
            val revisions = session!!.revisionList

            // The seed commit does not touch a.txt, so history is exactly c3, c2, c1 (newest first).
            assertEquals("expected 3 revisions, got ${revisions.size}", 3, revisions.size)
            assertEquals(listOf("c3", "c2", "c1"), revisions.map { it.commitMessage?.trim() })
            assertEquals(repo.node("."), revisions[0].revisionNumber.asString())
            assertEquals("v1\nv2\nv3\n", revisions[0].loadContent()?.decodeToString())
        }
    }

    /**
     * KNOWN LIMITATION (documented, not aspirational): on git-backed `.sl` repos — what
     * `sl init` produces — `sl log -f <file>` does NOT follow across renames, and the
     * rename-tracking path (`sl log --copies`) crashes upstream in Sapling
     * (`TypeError: 'gitfilelog' object is not iterable` in getrenamed). So
     * SaplingHistoryProvider shows only the post-rename history for a renamed file.
     * This test pins that real behavior; if a future `sl`/plugin change starts following
     * renames, it will fail — that is the signal to revisit the limitation, not a defect here.
     */
    fun testHistoryDoesNotFollowRenamesOnGitBackedRepo() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("old.txt", "v1\n"); repo.sl("add", "old.txt")
            repo.sl("commit", "-m", "add old")
            repo.writeFile("old.txt", "v1\nv2\n")
            repo.sl("commit", "-m", "edit old")
            repo.sl("rename", "old.txt", "new.txt")
            repo.sl("commit", "-m", "rename to new")

            val provider = SaplingHistoryProvider(project, SaplingCli("sl"))
            val filePath = VcsUtil.getFilePath(repo.root.resolve("new.txt").toFile(), false)
            val session = provider.createSessionFor(filePath)
            assertNotNull(session)
            val revisions = session!!.revisionList

            // Only the rename commit is reported — the pre-rename history under old.txt is not followed.
            assertEquals("history does not follow the rename", 1, revisions.size)
            assertEquals("rename to new", revisions[0].commitMessage?.trim())
            assertEquals(repo.node("."), revisions[0].revisionNumber.asString())
        }
    }
}
