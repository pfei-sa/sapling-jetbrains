package io.github.pfeisa.sapling.changes

import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import java.nio.file.Files
import java.nio.file.Path
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

    // ------------------------------------------------------------------
    // Whole-comparison listing (next/previous-file navigation in the viewer)
    // ------------------------------------------------------------------

    private fun entry(path: String, status: SaplingStatusCode, copySource: String? = null) =
        SaplingStatusEntry(path, status, copySource)

    fun testStatusArgsForWholeUncommittedOmitPathFilter() {
        assertEquals(
            listOf("status", "-Tjson", "--copies"),
            statusArgsForComparison("Uncommitted", null, null),
        )
    }

    fun testStatusArgsForWholeHeadKeepRevFlag() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--rev", ".^"),
            statusArgsForComparison("Head", null, null),
        )
    }

    fun testStatusArgsForWholeStackKeepRevFlag() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--rev", "ancestor(.,interestingmaster())"),
            statusArgsForComparison("Stack", null, null),
        )
    }

    fun testStatusArgsForWholeCommitKeepChangeFlag() {
        assertEquals(
            listOf("status", "-Tjson", "--copies", "--change", "abc123"),
            statusArgsForComparison("Commit", "abc123", null),
        )
    }

    fun testStatusArgsForWholeComparisonOfUnknownTypeAreNull() {
        assertNull(statusArgsForComparison("SinceLastCodeReviewSubmit", null, null))
    }

    fun testChangesForComparisonMapEveryEntryInOrder() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(
                entry("src/A.kt", SaplingStatusCode.MODIFIED),
                entry("src/B.kt", SaplingStatusCode.ADDED),
                entry("src/C.kt", SaplingStatusCode.REMOVED),
            ),
            root,
        )
        assertEquals(listOf("src/A.kt", "src/B.kt", "src/C.kt"), changes.map { it.path })
        assertEquals("abc123^", (changes[0].change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
        assertNull(changes[1].change.beforeRevision)
        assertNull(changes[2].change.afterRevision)
    }

    fun testChangesForComparisonUseWorkingCopyAfterSideForUncommitted() {
        val changes = changesForComparison(
            "Uncommitted",
            null,
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED), entry("src/B.kt", SaplingStatusCode.UNTRACKED)),
            root,
        )
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.change.afterRevision is CurrentContentRevision })
        assertEquals(".", (changes[0].change.beforeRevision!!.revisionNumber as SaplingRevisionNumber).hash)
    }

    fun testChangesForComparisonDropUndiffableEntries() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(
                entry("src/A.kt", SaplingStatusCode.MODIFIED),
                entry("src/Clean.kt", SaplingStatusCode.CLEAN),
                entry("build/out.txt", SaplingStatusCode.IGNORED),
            ),
            root,
        )
        assertEquals(listOf("src/A.kt"), changes.map { it.path })
    }

    fun testChangesForComparisonCollapseRenameToOneEntry() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(
                entry("src/New.kt", SaplingStatusCode.ADDED, copySource = "src/Old.kt"),
                entry("src/Old.kt", SaplingStatusCode.REMOVED),
            ),
            root,
        )
        assertEquals(listOf("src/New.kt"), changes.map { it.path })
        assertTrue(changes.single().change.beforeRevision!!.file.path.endsWith("src/Old.kt"))
    }

    fun testChangesForComparisonOfUnsupportedTypeIsEmpty() {
        val changes = changesForComparison(
            "SinceLastCodeReviewSubmit",
            null,
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED)),
            root,
        )
        assertTrue(changes.isEmpty())
    }

    fun testIndexOfComparisonChangeFindsClickedFile() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED), entry("src/B.kt", SaplingStatusCode.MODIFIED)),
            root,
        )
        assertEquals(1, indexOfComparisonChange(changes, "src/B.kt"))
    }

    fun testIndexOfComparisonChangeMatchesRenameSource() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(
                entry("src/New.kt", SaplingStatusCode.ADDED, copySource = "src/Old.kt"),
                entry("src/Old.kt", SaplingStatusCode.REMOVED),
            ),
            root,
        )
        assertEquals(0, indexOfComparisonChange(changes, "src/Old.kt"))
    }

    fun testIndexOfComparisonChangeIsNegativeWhenAbsent() {
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED)),
            root,
        )
        assertEquals(-1, indexOfComparisonChange(changes, "src/Missing.kt"))
    }

    // ------------------------------------------------------------------
    // Containment guards applied to every sibling, not just the clicked file
    // ------------------------------------------------------------------

    /** A real temp dir, since the working-copy guard resolves symlinks and requires existence. */
    private fun withRealRepo(body: (Path) -> Unit) {
        val dir = Files.createTempDirectory("cmp-guard-")
        try {
            body(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    fun testContainedComparisonChangesKeepWorkingCopyFilesOnDisk() = withRealRepo { dir ->
        Files.writeString(dir.resolve("a.txt"), "a\n")
        val changes = changesForComparison(
            "Uncommitted",
            null,
            listOf(entry("a.txt", SaplingStatusCode.MODIFIED)),
            dir,
        )
        assertEquals(listOf("a.txt"), containedComparisonChanges(changes, dir.toString()).map { it.path })
    }

    fun testContainedComparisonChangesDropWorkingCopyFilesAbsentFromDisk() = withRealRepo { dir ->
        val changes = changesForComparison(
            "Uncommitted",
            null,
            listOf(entry("gone.txt", SaplingStatusCode.MODIFIED)),
            dir,
        )
        assertEquals(1, changes.size)
        assertTrue(containedComparisonChanges(changes, dir.toString()).isEmpty())
    }

    fun testContainedComparisonChangesKeepCommitEntriesAbsentFromDisk() = withRealRepo { dir ->
        // A commit diff reads both sides via `sl cat`, so the file need not exist in the working copy.
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(entry("deleted/long/ago.txt", SaplingStatusCode.MODIFIED)),
            dir,
        )
        assertEquals(
            listOf("deleted/long/ago.txt"),
            containedComparisonChanges(changes, dir.toString()).map { it.path },
        )
    }

    fun testContainedComparisonChangesDropPathsEscapingTheRepo() = withRealRepo { dir ->
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(entry("../outside.txt", SaplingStatusCode.MODIFIED), entry("inside.txt", SaplingStatusCode.MODIFIED)),
            dir,
        )
        assertEquals(2, changes.size)
        assertEquals(listOf("inside.txt"), containedComparisonChanges(changes, dir.toString()).map { it.path })
    }

    fun testContainedComparisonChangesDropEscapingCopySource() = withRealRepo { dir ->
        val changes = changesForComparison(
            "Commit",
            "abc123",
            listOf(entry("inside.txt", SaplingStatusCode.ADDED, copySource = "../outside.txt")),
            dir,
        )
        assertEquals(1, changes.size)
        assertTrue(containedComparisonChanges(changes, dir.toString()).isEmpty())
    }

    fun testContainedComparisonChangesKeepWorkingCopyDeletionsAbsentFromDisk() = withRealRepo { dir ->
        // REMOVED has no working-copy after side, so it is lexical-only and survives.
        val changes = changesForComparison(
            "Uncommitted",
            null,
            listOf(entry("removed.txt", SaplingStatusCode.REMOVED)),
            dir,
        )
        assertEquals(
            listOf("removed.txt"),
            containedComparisonChanges(changes, dir.toString()).map { it.path },
        )
    }

    // ------------------------------------------------------------------
    // Untracked files are navigable siblings only where ISL lists them
    // ------------------------------------------------------------------

    fun testHeadComparisonExcludesUntrackedFromTheNavigableSet() {
        val changes = changesForComparison(
            "Head",
            null,
            listOf(
                entry("src/A.kt", SaplingStatusCode.MODIFIED),
                entry(".idea/workspace.xml", SaplingStatusCode.UNTRACKED),
                entry("notes.txt", SaplingStatusCode.UNTRACKED),
            ),
            root,
        )
        assertEquals(listOf("src/A.kt"), changes.map { it.path })
    }

    fun testStackComparisonExcludesUntrackedFromTheNavigableSet() {
        val changes = changesForComparison(
            "Stack",
            null,
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED), entry("notes.txt", SaplingStatusCode.UNTRACKED)),
            root,
        )
        assertEquals(listOf("src/A.kt"), changes.map { it.path })
    }

    fun testUncommittedComparisonKeepsUntrackedBecauseIslListsThem() {
        val changes = changesForComparison(
            "Uncommitted",
            null,
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED), entry("notes.txt", SaplingStatusCode.UNTRACKED)),
            root,
        )
        assertEquals(listOf("src/A.kt", "notes.txt"), changes.map { it.path })
    }

    fun testUntrackedClickUnderHeadFallsBackToASingleFileDiff() {
        // Not in the navigable set -> index -1 -> IdeBridge opens the clicked file on its own,
        // which is exactly the pre-navigation behaviour.
        val changes = changesForComparison(
            "Head",
            null,
            listOf(entry("src/A.kt", SaplingStatusCode.MODIFIED), entry("notes.txt", SaplingStatusCode.UNTRACKED)),
            root,
        )
        assertEquals(-1, indexOfComparisonChange(changes, "notes.txt"))
        // ...and the single-file path still produces a diff for it.
        assertNotNull(changeForComparison("Head", null, SaplingStatusCode.UNTRACKED, null, root, "notes.txt"))
    }
}
