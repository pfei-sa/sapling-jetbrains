package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.blame.parseSaplingAnnotate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard: `sl annotate -Tjson` emits a nested `[{path, lines:[...]}]` shape with a
 * float epoch. parseSaplingAnnotate already models this; this test fails loudly if the shape drifts.
 */
class SaplingAnnotateRealRepoTest {

    @Test
    fun parsesAnnotateWithPerLineBlame() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "hello\n"); repo.sl("add", "a.txt")
            repo.sl("commit", "-m", "line one")
            val firstNode = repo.node(".")
            repo.writeFile("a.txt", "hello\nworld\n")
            repo.sl("commit", "-m", "line two")
            val secondNode = repo.node(".")

            val res = repo.sl("annotate", "-Tjson", "a.txt")
            assertTrue("sl annotate failed: ${res.stderr}", res.success)
            val lines = parseSaplingAnnotate(res.stdout)

            assertEquals(2, lines.size)
            assertEquals(1, lines[0].lineNumber)
            assertEquals("hello\n", lines[0].content)
            assertEquals(firstNode, lines[0].node)
            assertEquals(2, lines[1].lineNumber)
            assertEquals("world\n", lines[1].content)
            assertEquals(secondNode, lines[1].node)
            assertEquals("Sapling IT <it@example.com>", lines[0].author)
            assertTrue("epoch parsed from float", lines[0].dateEpochSeconds > 0)
        }
    }
}
