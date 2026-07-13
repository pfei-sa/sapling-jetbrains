package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslRefreshDecisionTest {

    private val isl = "Sapling ISL"

    @Test fun refreshesWhenLeavingIslForEditor() {
        // current == null means no tool window active → the editor has focus
        assertTrue(IslRefreshDecision.shouldRefreshOnStateChange(isl, null, isl))
    }

    @Test fun refreshesWhenLeavingIslForAnotherToolWindow() {
        assertTrue(IslRefreshDecision.shouldRefreshOnStateChange(isl, "Project", isl))
    }

    @Test fun noRefreshWhenEnteringIsl() {
        assertFalse(IslRefreshDecision.shouldRefreshOnStateChange(null, isl, isl))
        assertFalse(IslRefreshDecision.shouldRefreshOnStateChange("Project", isl, isl))
    }

    @Test fun noRefreshWhenIslStaysActive() {
        assertFalse(IslRefreshDecision.shouldRefreshOnStateChange(isl, isl, isl))
    }

    @Test fun noRefreshWhenIslNeverInvolved() {
        assertFalse(IslRefreshDecision.shouldRefreshOnStateChange("Project", null, isl))
        assertFalse(IslRefreshDecision.shouldRefreshOnStateChange(null, null, isl))
    }
}
