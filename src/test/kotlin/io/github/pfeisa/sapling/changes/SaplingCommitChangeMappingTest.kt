package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class SaplingCommitChangeMappingTest : BasePlatformTestCase() {

    private val cli = SaplingCli()
    private val before = SaplingRevisionNumber("parent00")
    private val after = SaplingRevisionNumber("commit11")

    fun testModifiedHasBothSidesAtRevisions() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = commitStatusEntryToChange(
            SaplingStatusEntry("a.txt", SaplingStatusCode.MODIFIED, null), root, before, after, cli
        )
        assertNotNull(change)
        // Both sides are content-at-revision, NOT CurrentContentRevision.
        assertTrue(change!!.beforeRevision is SaplingContentRevision)
        assertTrue(change.afterRevision is SaplingContentRevision)
        assertEquals("parent00", change.beforeRevision!!.revisionNumber.asString())
        assertEquals("commit11", change.afterRevision!!.revisionNumber.asString())
        assertEquals(FileStatus.MODIFIED, change.fileStatus)
    }

    fun testAddedHasNoBefore() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = commitStatusEntryToChange(
            SaplingStatusEntry("new.txt", SaplingStatusCode.ADDED, null), root, before, after, cli
        )
        assertNotNull(change)
        assertNull(change!!.beforeRevision)
        assertEquals("commit11", change.afterRevision!!.revisionNumber.asString())
        assertEquals(FileStatus.ADDED, change.fileStatus)
    }

    fun testAddedWithCopySourceHasBeforeFromSourceAtParent() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = commitStatusEntryToChange(
            SaplingStatusEntry("copied.txt", SaplingStatusCode.ADDED, "original.txt"), root, before, after, cli
        )
        assertNotNull(change)
        assertEquals("original.txt", change!!.beforeRevision!!.file.name)
        assertEquals("parent00", change.beforeRevision!!.revisionNumber.asString())
        assertEquals("copied.txt", change.afterRevision!!.file.name)
        assertEquals("commit11", change.afterRevision!!.revisionNumber.asString())
    }

    fun testRemovedHasNoAfter() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = commitStatusEntryToChange(
            SaplingStatusEntry("gone.txt", SaplingStatusCode.REMOVED, null), root, before, after, cli
        )
        assertNotNull(change)
        assertEquals("parent00", change!!.beforeRevision!!.revisionNumber.asString())
        assertNull(change.afterRevision)
        assertEquals(FileStatus.DELETED, change.fileStatus)
    }

    fun testRootCommitAddHasNullBeforeEvenIfBeforeRevNull() {
        val root = Paths.get(myFixture.tempDirPath)
        val change = commitStatusEntryToChange(
            SaplingStatusEntry("first.txt", SaplingStatusCode.ADDED, null), root, null, after, cli
        )
        assertNotNull(change)
        assertNull(change!!.beforeRevision)
        assertEquals("commit11", change.afterRevision!!.revisionNumber.asString())
    }

    fun testCleanIsNotAChange() {
        val root = Paths.get(myFixture.tempDirPath)
        assertNull(
            commitStatusEntryToChange(SaplingStatusEntry("c.txt", SaplingStatusCode.CLEAN, null), root, before, after, cli)
        )
    }
}
