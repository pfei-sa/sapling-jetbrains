package io.github.pfeisa.sapling.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths

class SaplingPathsTest {

    private val root = "/repo"

    @get:Rule
    val tmp = TemporaryFolder()

    @Test fun repoRootResolvesDotSlRoot() {
        val repo = tmp.newFolder("sl-repo").toPath()
        Files.createDirectory(repo.resolve(".sl"))
        val nested = Files.createDirectories(repo.resolve("a/b"))
        assertEquals(repo, SaplingPaths.repoRoot(nested))
    }

    // Regression: dotgit-mode repos (`.git`, no `.sl`) must resolve too, or every read
    // provider bails out and the mapped Sapling VCS tab shows nothing. See CLAUDE.md.
    @Test fun repoRootResolvesDotgitModeRoot() {
        val repo = tmp.newFolder("dotgit-repo").toPath()
        Files.createDirectory(repo.resolve(".git"))
        val nested = Files.createDirectories(repo.resolve("src/main"))
        assertEquals(repo, SaplingPaths.repoRoot(nested))
    }

    @Test fun repoRootNullWhenNeitherPresent() {
        val nested = tmp.newFolder("plain", "sub").toPath()
        assertNull(SaplingPaths.repoRoot(nested))
    }

    @Test fun rejectsAbsolutePath() {
        assertNull(resolveWithinRepoLexical(root, "/etc/passwd"))
    }

    @Test fun rejectsEscapingRelativePath() {
        assertNull(resolveWithinRepoLexical(root, "../outside.txt"))
        assertNull(resolveWithinRepoLexical(root, "../../etc/passwd"))
        assertNull(resolveWithinRepoLexical(root, "src/../../outside.txt"))
    }

    @Test fun acceptsNormalInRepoRelativePath() {
        val result = resolveWithinRepoLexical(root, "src/A.kt")
        assertEquals(Paths.get("/repo/src/A.kt"), result)
        assertTrue(result!!.startsWith(Paths.get(root)))
    }
}
