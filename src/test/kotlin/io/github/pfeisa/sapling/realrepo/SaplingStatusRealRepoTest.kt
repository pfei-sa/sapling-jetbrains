package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.parseSaplingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingStatusRealRepoTest {

    @Test
    fun parsesModifiedRemovedCopiedUntracked() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            repo.writeFile("mod.txt", "a\n"); repo.sl("add", "mod.txt")
            repo.writeFile("del.txt", "b\n"); repo.sl("add", "del.txt")
            repo.writeFile("src.txt", "c\n"); repo.sl("add", "src.txt")
            repo.sl("commit", "-m", "base")

            repo.writeFile("mod.txt", "a\nchanged\n")     // M
            repo.sl("remove", "del.txt")                   // R
            repo.sl("copy", "src.txt", "copy.txt")         // A + copy=src.txt
            repo.writeFile("new.txt", "n\n")               // ?

            val res = repo.sl("status", "-Tjson", "--copies")
            assertTrue("sl status failed: ${res.stderr}", res.success)
            val byPath = parseSaplingStatus(res.stdout).associateBy { it.path }

            assertEquals(SaplingStatusCode.MODIFIED, byPath["mod.txt"]?.status)
            assertEquals(SaplingStatusCode.REMOVED, byPath["del.txt"]?.status)
            assertEquals(SaplingStatusCode.ADDED, byPath["copy.txt"]?.status)
            assertEquals("src.txt", byPath["copy.txt"]?.copySource)
            assertEquals(SaplingStatusCode.UNTRACKED, byPath["new.txt"]?.status)
        }
    }
}
