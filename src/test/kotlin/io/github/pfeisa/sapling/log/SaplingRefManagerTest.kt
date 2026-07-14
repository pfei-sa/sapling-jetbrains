package io.github.pfeisa.sapling.log

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hermetic guard for [SaplingRefManager] grouping. Uses a minimal [VcsRef] fake — the ref manager
 * only reads the name (for sorting) and the type (for colors); commit-hash and root are never
 * touched, so they throw to prove that.
 */
class SaplingRefManagerTest {

    private val manager = SaplingRefManager()

    private fun ref(refName: String): VcsRef = object : VcsRef {
        override fun getCommitHash(): Hash = throw UnsupportedOperationException()
        override fun getName(): String = refName
        override fun getType(): VcsRefType = SaplingRefManager.BOOKMARK
        override fun getRoot(): VirtualFile = throw UnsupportedOperationException()
    }

    @Test
    fun groupForBranchFilterReturnsOneNamedGroupSortedByName() {
        val groups = manager.groupForBranchFilter(listOf(ref("wip"), ref("main"), ref("dev")))
        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals("Bookmarks", group.name)
        assertEquals(listOf("dev", "main", "wip"), group.refs.map { it.name })
        // Three refs of the same type → getColors() caps at 2 per type (mirrors the platform
        // SimpleRefGroup). Three distinguishes capped from un-capped: an un-capped one-color-per-ref
        // regression would yield 3 here, so pinning size==2 actually guards the cap.
        assertEquals(2, group.colors.size)
    }

    @Test
    fun groupForTableReturnsOneNamedGroup() {
        val groups = manager.groupForTable(listOf(ref("main")), compact = true, showTagNames = false)
        assertEquals(1, groups.size)
        assertEquals("Bookmarks", groups.single().name)
        assertEquals(listOf("main"), groups.single().refs.map { it.name })
    }

    @Test
    fun emptyRefsProduceNoGroups() {
        assertTrue(manager.groupForBranchFilter(emptyList()).isEmpty())
        assertTrue(manager.groupForTable(emptyList(), compact = false, showTagNames = false).isEmpty())
    }
}
