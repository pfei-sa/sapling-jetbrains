package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.log.buildLogFilterArgs
import io.github.pfeisa.sapling.log.parseSaplingLog
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogFilterRealRepoTest {

    @Test
    fun textFilterNarrowsToMatchingMessages() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "1\n"); repo.sl("add", "a.txt"); repo.sl("commit", "-m", "alpha change")
            repo.writeFile("b.txt", "1\n"); repo.sl("add", "b.txt"); repo.sl("commit", "-m", "beta change")

            val args = buildLogFilterArgs(emptyList(), null, null, "alpha", emptyList(), emptyList())
            val res = repo.sl("log", "-Tjson", *args.toTypedArray())
            assertTrue("sl log (filtered) failed: ${res.stderr}", res.success)
            val entries = parseSaplingLog(res.stdout)
            assertTrue("only the 'alpha' commit matches", entries.all { it.description.contains("alpha") })
            assertTrue("the alpha commit is present", entries.any { it.description.contains("alpha") })
        }
    }

    @Test
    fun pathFilterNarrowsToCommitsTouchingThatFile() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("only-a.txt", "1\n"); repo.sl("add", "only-a.txt"); repo.sl("commit", "-m", "touch a")
            repo.writeFile("only-b.txt", "1\n"); repo.sl("add", "only-b.txt"); repo.sl("commit", "-m", "touch b")
            val bNode = repo.node(".")

            val args = buildLogFilterArgs(emptyList(), null, null, null, listOf("only-b.txt"), emptyList())
            val res = repo.sl("log", "-Tjson", *args.toTypedArray())
            assertTrue("sl log (filtered) failed: ${res.stderr}", res.success)
            val entries = parseSaplingLog(res.stdout)
            assertTrue("the b-touching commit is present", entries.any { it.node == bNode })
            assertTrue("the a-only commit is excluded", entries.none { it.description == "touch a" })
        }
    }
}
