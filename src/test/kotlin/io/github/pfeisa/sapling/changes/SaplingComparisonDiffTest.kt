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

    fun testHeadModifiedIsDotParentBaseVsWorkingCopy() {
        val change = changeForComparison("Head", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt")!!
        assertEquals(".^", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testHeadAddedHasNoBeforeSide() {
        val change = changeForComparison("Head", null, SaplingStatusCode.ADDED, null, root, "src/A.kt")!!
        assertNull(change.beforeRevision)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testHeadAddedWithCopySourceDiffsAgainstSourceAtDotParent() {
        val change = changeForComparison("Head", null, SaplingStatusCode.ADDED, "src/Old.kt", root, "src/A.kt")!!
        val before = change.beforeRevision!!
        assertEquals(".^", (before.revisionNumber as SaplingRevisionNumber).hash)
        assertTrue(before.file.path.endsWith("src/Old.kt"))
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testHeadRemovedHasNoAfterSide() {
        val change = changeForComparison("Head", null, SaplingStatusCode.REMOVED, null, root, "src/A.kt")!!
        assertEquals(".^", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertNull(change.afterRevision)
    }

    fun testStackModifiedIsStackBaseVsWorkingCopy() {
        val change = changeForComparison("Stack", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt")!!
        assertEquals("ancestor(.,interestingmaster())", (change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertTrue(change.afterRevision is CurrentContentRevision)
    }

    fun testUnknownTypeReturnsNull() {
        assertNull(changeForComparison("SinceLastCodeReviewSubmit", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt"))
    }

    fun testStatusArgsForUncommittedHaveNoRevFlag() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--", "src/A.kt"),
            statusArgsForComparison("Uncommitted", null, "src/A.kt"),
        )
    }

    fun testStatusArgsForHeadDiffAgainstDotParent() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--rev", ".^", "--", "src/A.kt"),
            statusArgsForComparison("Head", null, "src/A.kt"),
        )
    }

    fun testStatusArgsForStackDiffAgainstStackBase() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--rev", "ancestor(.,interestingmaster())", "--", "src/A.kt"),
            statusArgsForComparison("Stack", null, "src/A.kt"),
        )
    }

    fun testStatusArgsForCommitUseChangeFlag() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--change", "abc123", "--", "src/A.kt"),
            statusArgsForComparison("Commit", "abc123", "src/A.kt"),
        )
    }

    fun testStatusArgsForCommitWithoutHashAreNull() {
        assertNull(statusArgsForComparison("Commit", null, "src/A.kt"))
    }

    fun testStatusArgsForUnknownTypeAreNull() {
        assertNull(statusArgsForComparison("SinceLastCodeReviewSubmit", null, "src/A.kt"))
    }

    fun testSupportedComparisonTypes() {
        assertTrue(isComparisonTypeSupported("Uncommitted"))
        assertTrue(isComparisonTypeSupported("Head"))
        assertTrue(isComparisonTypeSupported("Stack"))
        assertTrue(isComparisonTypeSupported("Commit"))
        assertFalse(isComparisonTypeSupported("SinceLastCodeReviewSubmit"))
    }

    fun testCommitWithoutHashReturnsNull() {
        assertNull(changeForComparison("Commit", null, SaplingStatusCode.MODIFIED, null, root, "src/A.kt"))
    }

    fun testCleanFileReturnsNull() {
        assertNull(changeForComparison("Uncommitted", null, SaplingStatusCode.CLEAN, null, root, "src/A.kt"))
    }
}
