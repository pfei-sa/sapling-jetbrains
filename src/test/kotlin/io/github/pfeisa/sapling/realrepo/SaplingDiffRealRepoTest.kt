package io.github.pfeisa.sapling.realrepo

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.diff.SaplingDiffProvider

class SaplingDiffRealRepoTest : BasePlatformTestCase() {

    fun testCurrentRevisionIsParentAndContentAtRevisionsResolve() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "one\n"); repo.sl("add", "a.txt")
            repo.sl("commit", "-m", "v1")
            val firstNode = repo.node(".")
            repo.writeFile("a.txt", "one\ntwo\n")
            repo.sl("commit", "-m", "v2")
            val secondNode = repo.node(".")
            repo.writeFile("a.txt", "one\ntwo\nUNCOMMITTED\n")   // dirty working copy

            val vf = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(repo.root.resolve("a.txt"))
            assertNotNull(vf)
            val provider = SaplingDiffProvider(project, SaplingCli("sl"))

            // getCurrentRevision = working-copy parent (the latest commit, not the dirty file).
            val current = provider.getCurrentRevision(vf!!)
            assertNotNull("getCurrentRevision should resolve the working-copy parent", current)
            assertEquals(secondNode, current!!.asString())

            // content at the current (second) revision == committed v2 content.
            val atSecond = provider.createFileContent(current, vf)
            assertNotNull("createFileContent(current) should resolve", atSecond)
            assertEquals("one\ntwo\n", atSecond!!.content)

            // content at the first revision == v1 content (exercises `sl cat` at an older node).
            val atFirst = provider.createFileContent(SaplingRevisionNumber(firstNode), vf)
            assertNotNull("createFileContent(firstNode) should resolve", atFirst)
            assertEquals("one\n", atFirst!!.content)
        }
    }
}
