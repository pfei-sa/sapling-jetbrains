package io.github.pfeisa.sapling

import io.github.pfeisa.sapling.diff.SaplingBaseContentProvider
import io.github.pfeisa.sapling.diff.SaplingDiffProvider
import io.github.pfeisa.sapling.history.SaplingHistoryProvider
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A non-local VirtualFile (scratch buffer, diff dummy, in-memory LightVirtualFile) throws
 * UnsupportedOperationException from toNioPath(). The platform polls these predicates on
 * *arbitrary* editor files, so each must treat a non-local file as "not ours" instead of
 * letting that exception escape. Regression guard for the LineStatusTracker crash on /Dummy.txt.
 */
class SaplingNonLocalVfsGuardTest : BasePlatformTestCase() {

    fun testBaseContentProviderIsSupportedIsFalse() {
        val f = LightVirtualFile("Dummy.txt", "content")
        assertFalse(SaplingBaseContentProvider(project).isSupported(f))
    }

    fun testBaseContentProviderGetBaseRevisionIsNull() {
        val f = LightVirtualFile("Dummy.txt", "content")
        assertNull(SaplingBaseContentProvider(project).getBaseRevision(f))
    }

    fun testHistoryProviderCanShowHistoryForIsFalse() {
        val f = LightVirtualFile("Dummy.txt", "content")
        assertFalse(SaplingHistoryProvider(project).canShowHistoryFor(f))
    }

    fun testDiffProviderGetCurrentRevisionIsNull() {
        val f = LightVirtualFile("Dummy.txt", "content")
        assertNull(SaplingDiffProvider(project).getCurrentRevision(f))
    }

    fun testDiffProviderGetLastRevisionIsNull() {
        val f = LightVirtualFile("Dummy.txt", "content")
        assertNull(SaplingDiffProvider(project).getLastRevision(f))
    }
}
