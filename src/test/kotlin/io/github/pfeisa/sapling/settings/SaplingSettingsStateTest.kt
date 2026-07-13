package io.github.pfeisa.sapling.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SaplingSettingsStateTest {
    @Test fun defaults() {
        val s = SaplingSettings.State()
        assertEquals("sl", s.executablePath)
        assertFalse(s.autoOpenIsl)
    }

    @Test fun copyChangesOneField() {
        val s = SaplingSettings.State().copy(executablePath = "/opt/sl")
        assertEquals("/opt/sl", s.executablePath)
        assertFalse(s.autoOpenIsl)
    }
}
