package io.github.pfeisa.sapling.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Constructs a fresh SaplingSettings directly (no application service) so the test is hermetic and
 * cannot pollute — or be polluted by — the shared app-level service.
 */
class SaplingSettingsTest {

    @Test fun hideCommitUiDefaultsOn() {
        assertTrue(SaplingSettings().hideCommitUi)
    }

    @Test fun hideCommitUiRoundTrips() {
        val settings = SaplingSettings()
        settings.hideCommitUi = false
        assertFalse(settings.hideCommitUi)
        settings.hideCommitUi = true
        assertTrue(settings.hideCommitUi)
    }
}
