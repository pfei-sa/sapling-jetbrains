package io.github.pfeisa.sapling.rollback

import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingRevertTest {
    @Test
    fun buildsNoBackupRevertArgsWithPathSeparator() {
        assertEquals(
            listOf("revert", "--no-backup", "--", "a.txt", "dir/b.txt"),
            revertArgs(listOf("a.txt", "dir/b.txt")),
        )
    }

    @Test
    fun emptyPathsStillProduceCommandPrefix() {
        assertEquals(listOf("revert", "--no-backup", "--"), revertArgs(emptyList()))
    }
}
