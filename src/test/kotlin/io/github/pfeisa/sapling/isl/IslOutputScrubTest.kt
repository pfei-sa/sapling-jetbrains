package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IslOutputScrubTest {

    @Test
    fun stripsTokenBearingUrl() {
        val out = scrubSlWebOutput("cannot reach http://localhost:8081/androidStudio.html?token=abc123 now")
        assertFalse("URL host must be removed", out.contains("localhost"))
        assertFalse("token value must be removed", out.contains("abc123"))
    }

    @Test
    fun redactsBareTokenParam() {
        assertEquals("boom token=<redacted> tail", scrubSlWebOutput("boom token=SECRET tail"))
    }

    @Test
    fun leavesBenignTextUnchanged() {
        assertEquals("port 8081 is already in use", scrubSlWebOutput("port 8081 is already in use"))
    }
}
