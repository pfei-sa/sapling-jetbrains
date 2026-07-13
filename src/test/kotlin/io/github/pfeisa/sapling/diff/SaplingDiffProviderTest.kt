package io.github.pfeisa.sapling.diff

import io.github.pfeisa.sapling.changes.SaplingContentRevision
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class SaplingDiffProviderTest : BasePlatformTestCase() {

    fun testCreateFileContentReturnsSaplingContentRevision() {
        val root = Files.createTempDirectory("sl-diff-test")
        Files.createDirectory(root.resolve(".sl"))
        val sub = Files.createDirectories(root.resolve("sub"))
        val file = Files.writeString(sub.resolve("a.txt"), "hello")
        try {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
            assertNotNull("file should resolve in VFS", vf)

            val provider = SaplingDiffProvider(project)
            val content = provider.createFileContent(SaplingRevisionNumber("cafef00d"), vf!!)

            assertNotNull(content)
            assertTrue(content is SaplingContentRevision)
            assertEquals("cafef00d", content!!.revisionNumber.asString())
        } finally {
            deleteRecursively(root)
        }
    }

    private fun deleteRecursively(root: Path) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
