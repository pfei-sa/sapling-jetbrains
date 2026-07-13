package io.github.pfeisa.sapling.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `sl status -i --terse=i` collapses a fully-ignored directory to one entry with a trailing slash
 * (e.g. `build/`); a lone ignored file has none. [ignoredFileTarget] turns that into a
 * (relative-path, isDirectory) pair so the whole subtree can be greyed with one `processIgnoredFile`.
 */
class SaplingIgnoredFileTargetTest {

    @Test
    fun trailingSlashMarksDirectoryAndIsStripped() {
        val target = ignoredFileTarget("build/")
        assertEquals("build", target.relativePath)
        assertTrue(target.isDirectory)
    }

    @Test
    fun nestedIgnoredDirectoryKeepsInnerSlashes() {
        val target = ignoredFileTarget("docs/superpowers/")
        assertEquals("docs/superpowers", target.relativePath)
        assertTrue(target.isDirectory)
    }

    @Test
    fun hiddenDirectoryIsStillADirectory() {
        val target = ignoredFileTarget(".claude/")
        assertEquals(".claude", target.relativePath)
        assertTrue(target.isDirectory)
    }

    @Test
    fun loneIgnoredFileIsNotADirectory() {
        val target = ignoredFileTarget("logs/output.log")
        assertEquals("logs/output.log", target.relativePath)
        assertFalse(target.isDirectory)
    }

    @Test
    fun rootLevelIgnoredFileIsNotADirectory() {
        val target = ignoredFileTarget("scratch.tmp")
        assertEquals("scratch.tmp", target.relativePath)
        assertFalse(target.isDirectory)
    }
}
