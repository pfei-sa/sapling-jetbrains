package io.github.pfeisa.sapling.changes

import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.status.SaplingStatusCode
import java.nio.file.Paths

class SaplingComparisonDiffTest : BasePlatformTestCase() {

    private val root = Paths.get("/repo")

    fun testUncommittedModifiedIsDotBaseVsWorkingCopy() {
        val change = changeForComparison("Uncommitted", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt")!!
        assertEquals(".", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testUncommittedAddedHasNoBeforeSide() {
        val change = changeForComparison("Uncommitted", null, SaplingStatusCode.ADDED, null, root, "src/A.kt")!!
        assertNull(change.beforeRevision)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testUncommittedUntrackedHasNoBeforeSide() {
        val change = changeForComparison("Uncommitted", null, SaplingStatusCode.UNTRACKED, null, root, "src/A.kt")!!
        assertNull(change.beforeRevision)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testUncommittedRemovedHasNoAfterSide() {
        val change = changeForComparison("Uncommitted", null, SaplingStatusCode.REMOVED, null, root, "src/A.kt")!!
        assertEquals(".", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertNull(change.afterRevision)
    }

    fun testCommitModifiedIsParentVsCommit() {
        val change = changeForComparison("Commit", "abc123", SaplingStatusCode.MODIFIED, null, root, "src/A.kt")!!
        assertEquals("abc123^", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertEquals("abc123", (change.afterRevision!!.revisionNumber as SaplingRevisionNumber).hash)
    }

    fun testCommitAddedHasNoBeforeSide() {
        val change = changeForComparison("Commit", "abc123", SaplingStatusCode.ADDED, null, root, "src/A.kt")!!
        assertNull(change.beforeRevision)
        assertEquals("abc123", (change.afterRevision!!.revisionNumber as SaplingRevisionNumber).hash)
    }

    fun testCommitRemovedHasNoAfterSide() {
        val change = changeForComparison("Commit", "abc123", SaplingStatusCode.REMOVED, null, root, "src/A.kt")!!
        assertEquals("abc123^", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertNull(change.afterRevision)
    }

    fun testUnknownTypeReturnsNull() {
        assertNull(changeForComparison("Stack", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt"))
    }

    fun testCommitWithoutHashReturnsNull() {
        assertNull(changeForComparison("Commit", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt"))
    }

    fun testCleanFileReturnsNull() {
        assertNull(changeForComparison("Uncommitted", null, SaplingStatusCode.CLEAN, null, root, "src/A.kt"))
    }
}
