package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.rollback.revertArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SaplingRevertRealRepoTest {
    @Test
    fun revertRestoresModifiedFileWithoutOrigBackup() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("f.txt", "one\n")
            repo.sl("add", "f.txt")
            repo.sl("commit", "-m", "base")

            repo.writeFile("f.txt", "one\nchanged\n")
            val status = repo.sl("status", "f.txt")
            assertTrue("expected modified: ${status.stdout}", status.stdout.trimStart().startsWith("M"))

            val res = repo.sl(*revertArgs(listOf("f.txt")).toTypedArray())
            assertTrue("sl revert failed: ${res.stderr}", res.success)

            assertEquals("one\n", Files.readString(repo.root.resolve("f.txt")))
            assertFalse("no .orig backup expected", Files.exists(repo.root.resolve("f.txt.orig")))
        }
    }
}
