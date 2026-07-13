package io.github.pfeisa.sapling.detection

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises the `isRoot(VirtualFile)` entry point end-to-end (the VirtualFile ->
 * Path bridge plus delegation to the detector), which the pure-JUnit tests cannot
 * reach. The detector's own branch logic is covered by SaplingRepoDetectorTest.
 */
class SaplingVcsRootCheckerVfsTest : BasePlatformTestCase() {

    private val checker = SaplingVcsRootChecker()

    fun testIsRootTrueForDotSlDirectory() {
        val dir = Files.createTempDirectory("sl-root")
        Files.createDirectory(dir.resolve(".sl"))
        try {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
            assertNotNull("temp dir should resolve in VFS", vf)
            assertTrue(checker.isRoot(vf!!))
        } finally {
            deleteRecursively(dir)
        }
    }

    fun testIsRootFalseForPlainDirectory() {
        val dir = Files.createTempDirectory("plain-root")
        try {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
            assertNotNull("temp dir should resolve in VFS", vf)
            assertFalse(checker.isRoot(vf!!))
        } finally {
            deleteRecursively(dir)
        }
    }

    private fun deleteRecursively(root: Path) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
