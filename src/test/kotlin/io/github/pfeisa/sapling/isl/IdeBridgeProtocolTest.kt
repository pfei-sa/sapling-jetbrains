package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdeBridgeProtocolTest {

    @Test fun parsesOpenMessage() {
        val msg = parseBridgeMessage("""{"t":"open","path":"src/A.kt","line":42}""")!!
        assertEquals("open", msg.t)
        assertEquals("src/A.kt", msg.path)
        assertEquals(42, msg.line)
        assertNull(msg.comparison)
    }

    @Test fun parsesUncommittedDiffMessage() {
        val msg = parseBridgeMessage("""{"t":"diff","path":"src/A.kt","comparison":{"type":"Uncommitted"}}""")!!
        assertEquals("diff", msg.t)
        assertEquals("Uncommitted", msg.comparison!!.type)
        assertNull(msg.comparison!!.hash)
    }

    @Test fun parsesCommitDiffMessageWithHash() {
        val msg = parseBridgeMessage("""{"t":"diff","path":"a","comparison":{"type":"Commit","hash":"abc123"}}""")!!
        assertEquals("Commit", msg.comparison!!.type)
        assertEquals("abc123", msg.comparison!!.hash)
    }

    @Test fun ignoresUnknownFields() {
        val msg = parseBridgeMessage("""{"t":"diff","path":"a","comparison":{"type":"Commit","extra":9},"junk":true}""")!!
        assertEquals("Commit", msg.comparison!!.type)
    }

    @Test fun malformedJsonReturnsNull() {
        assertNull(parseBridgeMessage("not json"))
        assertNull(parseBridgeMessage("""{"t":}"""))
    }
}
