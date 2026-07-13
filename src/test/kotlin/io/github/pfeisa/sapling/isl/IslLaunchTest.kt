package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslLaunchTest {

    @Test
    fun parsesSuccessfulLaunch() {
        val json = """
            {"url":"http://localhost:8081/androidStudio.html?token=abc&cwd=%2Frepo",
             "port":8081,"token":"abc","pid":1521158,"wasServerReused":true,
             "logFileLocation":"/tmp/isl.log","cwd":"/repo","command":"sl"}
        """.trimIndent()

        val result = parseIslLaunch(json)

        assertTrue(result is IslLaunchResult.Ready)
        val info = (result as IslLaunchResult.Ready).info
        assertEquals(8081, info.port)
        assertEquals("abc", info.token)
        assertEquals(1521158L, info.pid)
        assertTrue(info.wasReused)
        assertTrue(info.url.contains("androidStudio.html"))
    }

    @Test
    fun parsesErrorObject() {
        val result = parseIslLaunch("""{"error":"port 8081 is already in use"}""")
        assertTrue(result is IslLaunchResult.Failed)
        assertEquals("port 8081 is already in use", (result as IslLaunchResult.Failed).error)
    }

    @Test
    fun treatsMissingFieldsAsFailure() {
        val result = parseIslLaunch("""{"port":8081}""")
        assertTrue(result is IslLaunchResult.Failed)
    }

    @Test
    fun treatsBlankAsFailure() {
        assertTrue(parseIslLaunch("") is IslLaunchResult.Failed)
    }

    @Test
    fun scrubsTokenBearingError() {
        val result = parseIslLaunch("""{"error":"cannot bind http://localhost:8081/?token=abc"}""")
        assertTrue(result is IslLaunchResult.Failed)
        val message = (result as IslLaunchResult.Failed).error
        assertFalse(message.contains("abc"))
        assertFalse(message.contains("localhost"))
    }
}
