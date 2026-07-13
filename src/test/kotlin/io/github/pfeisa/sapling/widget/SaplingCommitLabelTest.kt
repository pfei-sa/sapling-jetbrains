package io.github.pfeisa.sapling.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SaplingCommitLabelTest {
    @Test
    fun prefersActiveBookmark() {
        assertEquals("feature", commitLabel("feature", "abc1234", "fix a bug"))
    }

    @Test
    fun fallsBackToShortNodeAndSummary() {
        assertEquals("abc1234 fix a bug", commitLabel(null, "abc1234", "fix a bug"))
    }

    @Test
    fun nodeOnlyWhenSummaryBlank() {
        assertEquals("abc1234", commitLabel(null, "abc1234", "   "))
    }

    @Test
    fun blankBookmarkIsIgnored() {
        assertEquals("abc1234 msg", commitLabel("", "abc1234", "msg"))
    }
}
