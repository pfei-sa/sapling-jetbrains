package io.github.pfeisa.sapling.blame

import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingBlameTooltipTest {

    private fun commit(msg: String) =
        SaplingBlameCommit("6cb3884aad8aa5211c33324df9a913e22687f549", "Peng Fei", 1L, msg)

    @Test
    fun headerPlusFirstLineOfMessage() {
        val text = SaplingBlameTooltip.format(commit("Docs: make README concise\n\n- details"), "Jul 12 2026 14:22")
        assertEquals("6cb3884a — Peng Fei, Jul 12 2026 14:22\nDocs: make README concise", text)
    }

    @Test
    fun headerOnlyWhenMessageBlank() {
        val text = SaplingBlameTooltip.format(commit("   "), "Jul 12 2026 14:22")
        assertEquals("6cb3884a — Peng Fei, Jul 12 2026 14:22", text)
    }
}
