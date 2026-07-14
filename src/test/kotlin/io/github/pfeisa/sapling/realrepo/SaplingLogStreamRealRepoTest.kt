package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.log.parseStreamedCommitLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogStreamRealRepoTest {

    @Test
    fun streamsEveryCommitWithParents() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "1\n"); repo.sl("add", "a.txt"); repo.sl("commit", "-m", "c1")
            repo.writeFile("a.txt", "2\n"); repo.sl("commit", "-m", "c2")

            val template = "{node}\\0{p1node}\\0{p2node}\\0{date|hgdate}\\0{author}\\n"
            val res = repo.sl("log", "-T", template)
            assertTrue("sl log failed: ${res.stderr}", res.success)

            val commits = res.stdout.split("\n").mapNotNull { parseStreamedCommitLine(it) }
            // seed + c1 + c2
            assertEquals(3, commits.size)
            // Newest first: c2's single parent is c1's node.
            assertEquals(commits[1].node, commits[0].parents.single())
            // The oldest (seed/root) has no parents.
            assertTrue("root has no parents", commits.last().parents.isEmpty())
            assertTrue("timestamps parse positive", commits.all { it.timestampMs > 0 })
        }
    }
}
