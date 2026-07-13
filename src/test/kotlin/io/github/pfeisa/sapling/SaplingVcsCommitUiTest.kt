package io.github.pfeisa.sapling

import com.intellij.openapi.vcs.VcsType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.settings.SaplingSettings

/**
 * Light-platform coverage for the SaplingVcs commit-UI wiring. The in-memory fixture has no active
 * Sapling mapping, so `isSoleActiveVcs` is false here and `getType()` must stay `distributed`
 * regardless of the setting; the sole-VCS==true → centralized path is covered by the pure
 * SaplingCommitUiSuppressionTest. isCommitActionDisabled() reflects the setting directly.
 */
class SaplingVcsCommitUiTest : BasePlatformTestCase() {

    private val settings get() = SaplingSettings.getInstance()
    private var original = true

    override fun setUp() {
        super.setUp()
        original = settings.hideCommitUi
    }

    override fun tearDown() {
        try {
            settings.hideCommitUi = original
        } finally {
            super.tearDown()
        }
    }

    fun testCommitActionDisabledFollowsSetting() {
        val vcs = SaplingVcs(project)
        settings.hideCommitUi = true
        assertTrue(vcs.isCommitActionDisabled)
        settings.hideCommitUi = false
        assertFalse(vcs.isCommitActionDisabled)
    }

    fun testTypeStaysDistributedWhenNotSoleActiveVcs() {
        // No Sapling mapping is active in the light fixture → not sole → distributed, even with the
        // setting on (this is the guard that protects a co-mapped Git root).
        settings.hideCommitUi = true
        assertEquals(VcsType.distributed, SaplingVcs(project).type)
    }
}
