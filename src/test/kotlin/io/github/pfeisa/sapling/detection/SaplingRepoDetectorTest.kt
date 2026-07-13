package io.github.pfeisa.sapling.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SaplingRepoDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun detectsDirectoryContainingDotSl() {
        val root = tmp.newFolder("repo").toPath()
        Files.createDirectory(root.resolve(".sl"))
        assertTrue(SaplingRepoDetector.isSaplingRoot(root))
    }

    @Test
    fun rejectsDirectoryWithoutDotSl() {
        val root = tmp.newFolder("plain").toPath()
        assertFalse(SaplingRepoDetector.isSaplingRoot(root))
    }

    @Test
    fun walksUpToTheRoot() {
        val root = tmp.newFolder("repo2").toPath()
        Files.createDirectory(root.resolve(".sl"))
        val nested = Files.createDirectories(root.resolve("a/b/c"))
        assertEquals(root, SaplingRepoDetector.findRepoRoot(nested))
    }

    @Test
    fun returnsNullWhenNoRootAbove() {
        val nested = tmp.newFolder("x", "y").toPath()
        assertNull(SaplingRepoDetector.findRepoRoot(nested))
    }

    @Test
    fun workingCopyRootMatchesDotSl() {
        val root = tmp.newFolder("sl-repo").toPath()
        Files.createDirectory(root.resolve(".sl"))
        assertTrue(SaplingRepoDetector.isWorkingCopyRoot(root))
        val nested = Files.createDirectories(root.resolve("a/b"))
        assertEquals(root, SaplingRepoDetector.findWorkingCopyRoot(nested))
    }

    @Test
    fun workingCopyRootMatchesDotGit() {
        val root = tmp.newFolder("git-repo").toPath()
        Files.createDirectory(root.resolve(".git"))
        assertTrue(SaplingRepoDetector.isWorkingCopyRoot(root))
        val nested = Files.createDirectories(root.resolve("src/main"))
        assertEquals(root, SaplingRepoDetector.findWorkingCopyRoot(nested))
        // dotgit mode is NOT a `.sl` root — VCS ownership stays with Git4Idea.
        assertFalse(SaplingRepoDetector.isSaplingRoot(root))
        assertNull(SaplingRepoDetector.findRepoRoot(nested))
    }

    @Test
    fun workingCopyRootNullWhenNeitherPresent() {
        val nested = tmp.newFolder("plain", "sub").toPath()
        assertNull(SaplingRepoDetector.findWorkingCopyRoot(nested))
    }
}
