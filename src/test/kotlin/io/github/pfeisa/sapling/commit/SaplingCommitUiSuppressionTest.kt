package io.github.pfeisa.sapling.commit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingCommitUiSuppressionTest {

    private val sapling = "Sapling"

    // --- isSoleActiveVcs ---

    @Test fun soleWhenOnlySaplingActive() {
        assertTrue(SaplingCommitUiSuppression.isSoleActiveVcs(listOf(sapling), sapling))
    }

    @Test fun soleWhenMultipleButAllSapling() {
        // Defensive: the platform never lists the same VCS twice, but "all are Sapling" must hold.
        assertTrue(SaplingCommitUiSuppression.isSoleActiveVcs(listOf(sapling, sapling), sapling))
    }

    @Test fun notSoleWhenGitAlsoActive() {
        assertFalse(SaplingCommitUiSuppression.isSoleActiveVcs(listOf(sapling, "Git"), sapling))
    }

    @Test fun notSoleWhenNoVcsActive() {
        assertFalse(SaplingCommitUiSuppression.isSoleActiveVcs(emptyList(), sapling))
    }

    @Test fun notSoleWhenOnlyOtherVcsActive() {
        assertFalse(SaplingCommitUiSuppression.isSoleActiveVcs(listOf("Git"), sapling))
    }

    // --- shouldForceModalCommit ---

    @Test fun forcesModalWhenEnabledAndSole() {
        assertTrue(SaplingCommitUiSuppression.shouldForceModalCommit(hideCommitUi = true, saplingIsSoleActiveVcs = true))
    }

    @Test fun noForceWhenSettingOff() {
        assertFalse(SaplingCommitUiSuppression.shouldForceModalCommit(hideCommitUi = false, saplingIsSoleActiveVcs = true))
    }

    @Test fun noForceWhenNotSole() {
        assertFalse(SaplingCommitUiSuppression.shouldForceModalCommit(hideCommitUi = true, saplingIsSoleActiveVcs = false))
    }

    @Test fun noForceWhenBothFalse() {
        assertFalse(SaplingCommitUiSuppression.shouldForceModalCommit(hideCommitUi = false, saplingIsSoleActiveVcs = false))
    }
}
