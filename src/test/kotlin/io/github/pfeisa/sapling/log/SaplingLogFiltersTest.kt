package io.github.pfeisa.sapling.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaplingLogFiltersTest {

    @Test fun emptyFiltersProduceNoArgs() {
        assertEquals(emptyList<String>(), buildLogFilterArgs(emptyList(), null, null, null, emptyList(), emptyList()))
    }

    @Test fun usersEachBecomeADashU() {
        assertEquals(
            listOf("-u", "Alice", "-u", "Bob"),
            buildLogFilterArgs(listOf("Alice", "Bob"), null, null, null, emptyList(), emptyList()),
        )
    }

    @Test fun dateRangeUsesToSyntax() {
        assertEquals(
            listOf("-d", "2020-01-01 00:00:00 to 2021-01-01 00:00:00"),
            buildLogFilterArgs(emptyList(), "2020-01-01 00:00:00", "2021-01-01 00:00:00", null, emptyList(), emptyList()),
        )
    }

    @Test fun afterOnlyUsesGreaterThan() {
        assertEquals(
            listOf("-d", ">2020-01-01 00:00:00"),
            buildLogFilterArgs(emptyList(), "2020-01-01 00:00:00", null, null, emptyList(), emptyList()),
        )
    }

    @Test fun beforeOnlyUsesLessThan() {
        assertEquals(
            listOf("-d", "<2021-01-01 00:00:00"),
            buildLogFilterArgs(emptyList(), null, "2021-01-01 00:00:00", null, emptyList(), emptyList()),
        )
    }

    @Test fun textBecomesDashK() {
        assertEquals(listOf("-k", "fix bug"), buildLogFilterArgs(emptyList(), null, null, "fix bug", emptyList(), emptyList()))
    }

    @Test fun validHexHashesBecomeDashR() {
        assertEquals(
            listOf("-r", "abc123", "-r", "DEF456"),
            buildLogFilterArgs(emptyList(), null, null, null, emptyList(), listOf("abc123", "DEF456")),
        )
    }

    @Test fun nonHexHashIsDropped() {
        // An injection-shaped value is never hex, so it is never interpolated.
        val args = buildLogFilterArgs(emptyList(), null, null, null, emptyList(), listOf("abc; rm -rf /"))
        assertFalse("no -r emitted for a non-hex hash", args.contains("-r"))
    }

    @Test fun pathsGoLastAfterDoubleDash() {
        val args = buildLogFilterArgs(listOf("Alice"), null, null, "x", listOf("src/a.kt", "src/b.kt"), emptyList())
        assertEquals(listOf("-u", "Alice", "-k", "x", "--", "src/a.kt", "src/b.kt"), args)
    }

    @Test fun textWithQuoteStaysOneArg() {
        val args = buildLogFilterArgs(emptyList(), null, null, "foo'bar", emptyList(), emptyList())
        assertEquals(listOf("-k", "foo'bar"), args)
        assertTrue("the quoted value is a single argv element", args.contains("foo'bar"))
    }
}
