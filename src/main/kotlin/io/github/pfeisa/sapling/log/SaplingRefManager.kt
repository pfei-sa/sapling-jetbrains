package io.github.pfeisa.sapling.log

import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.impl.SimpleRefType
import java.awt.Color
import java.io.DataInput
import java.io.DataOutput

/**
 * Minimal ref manager for Sapling bookmarks.
 *
 * Bookmarks are Sapling's closest analogue to branches; they are rendered as a
 * single ref type colored in a bookmarks-green.  The `serialize`/`deserialize`
 * contract is satisfied by writing/reading the type name (the only type we have
 * is BOOKMARK, but this is open-coded defensively).
 */
class SaplingRefManager : VcsLogRefManager {

    companion object {
        val BOOKMARK: VcsRefType = SimpleRefType("BOOKMARK", true, Color(0x30, 0x80, 0x30))

        private const val TYPE_BOOKMARK = 0
    }

    // --- Ordering ---------------------------------------------------------

    override fun getLabelsOrderComparator(): Comparator<VcsRef> =
        Comparator.comparing { ref: VcsRef -> ref.name }

    override fun getBranchLayoutComparator(): Comparator<VcsRef> =
        Comparator.comparing { ref: VcsRef -> ref.name }

    // --- Grouping ---------------------------------------------------------

    /** Used by the branch-filter pop-up: one flat group of all bookmark refs. */
    override fun groupForBranchFilter(refs: Collection<VcsRef>): List<RefGroup> {
        if (refs.isEmpty()) return emptyList()
        return listOf(BookmarkRefGroup("Bookmarks", refs.sortedWith(getLabelsOrderComparator())))
    }

    /** Used by the commit-table ref column: one flat group per commit. */
    override fun groupForTable(
        refs: Collection<VcsRef>,
        compact: Boolean,
        showTagNames: Boolean,
    ): List<RefGroup> {
        if (refs.isEmpty()) return emptyList()
        return listOf(BookmarkRefGroup("Bookmarks", refs.sortedWith(getLabelsOrderComparator())))
    }

    /**
     * Local [RefGroup] implementation replacing `com.intellij.vcs.log.impl.SimpleRefGroup`. In 242
     * that class's primary constructor had a defaulted `expanded: Boolean` parameter, so a 2-arg
     * Kotlin call compiled to a reference to the synthetic default-args bridge
     * `<init>(String, List, boolean, int, DefaultConstructorMarker)`; builds 261/262 dropped the
     * parameter, making that bridge an unresolved-constructor binary incompatibility. Implementing
     * [RefGroup] directly (a stable public interface that only shrank — 242's `isExpanded()` was
     * dropped by 262) removes the coupling. `getColors()` mirrors SimpleRefGroup's own logic.
     */
    private class BookmarkRefGroup(
        private val groupName: String,
        private val groupRefs: List<VcsRef>,
    ) : RefGroup {
        override fun isExpanded(): Boolean = false
        override fun getName(): String = groupName
        override fun getRefs(): MutableList<VcsRef> = groupRefs.toMutableList()
        override fun getColors(): List<Color> =
            groupRefs.groupBy(VcsRef::getType).flatMap { (type, refsOfType) ->
                val color = type.backgroundColor
                if (refsOfType.size > 1) listOf(color, color) else listOf(color)
            }
    }

    // --- Serialization (used for persisting the log index) ----------------

    override fun serialize(out: DataOutput, type: VcsRefType) {
        // We only have one type; write 0 as a sentinel so the format is stable.
        out.writeInt(TYPE_BOOKMARK)
    }

    override fun deserialize(input: DataInput): VcsRefType {
        input.readInt() // consume the sentinel
        return BOOKMARK
    }

    // --- Favourites -------------------------------------------------------

    /** Sapling has no IDE-side favourites concept; always return false. */
    override fun isFavorite(ref: VcsRef): Boolean = false

    /** No-op: Sapling does not persist IDE-side favourites for bookmarks. */
    override fun setFavorite(ref: VcsRef, favorite: Boolean) { /* no-op */ }
}
