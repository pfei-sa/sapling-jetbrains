package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class SaplingChangeMappingTest : BasePlatformTestCase() {

    private val cli = SaplingCli()
    private val parent = SaplingRevisionNumber("abc123")

    fun testModifiedHasBeforeAndCurrentAfter() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = statusEntryToChange(
            SaplingStatusEntry("a.txt", SaplingStatusCode.MODIFIED, null), root, parent, cli
        )
        assertNotNull(change)
        assertTrue(change!!.beforeRevision is SaplingContentRevision)
        assertTrue(change.afterRevision is CurrentContentRevision)
        assertEquals(FileStatus.MODIFIED, change.fileStatus)
    }

    fun testAddedHasNoBefore() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = statusEntryToChange(
            SaplingStatusEntry("new.txt", SaplingStatusCode.ADDED, null), root, parent, cli
        )
        assertNotNull(change)
        assertNull(change!!.beforeRevision)
        assertTrue(change.afterRevision is CurrentContentRevision)
        assertEquals(FileStatus.ADDED, change.fileStatus)
    }

    fun testRemovedHasNoAfter() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = statusEntryToChange(
            SaplingStatusEntry("gone.txt", SaplingStatusCode.REMOVED, null), root, parent, cli
        )
        assertNotNull(change)
        assertNotNull(change!!.beforeRevision)
        assertNull(change.afterRevision)
        assertEquals(FileStatus.DELETED, change.fileStatus)
    }

    fun testCleanIsNotAChange() {
        val root = Paths.get(myFixture.tempDirPath)
        assertNull(
            statusEntryToChange(SaplingStatusEntry("c.txt", SaplingStatusCode.CLEAN, null), root, parent, cli)
        )
    }

    fun testAddedCopyHasBeforeFromSource() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = statusEntryToChange(
            SaplingStatusEntry("copied.txt", SaplingStatusCode.ADDED, "original.txt"), root, parent, cli
        )
        assertNotNull(change)
        assertTrue(change!!.beforeRevision is SaplingContentRevision)
        assertEquals("original.txt", change.beforeRevision!!.file.name)
        assertTrue(change.afterRevision is CurrentContentRevision)
        assertEquals("copied.txt", change.afterRevision!!.file.name)
    }
}
