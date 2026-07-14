package io.github.pfeisa.sapling.log

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails

/**
 * Adapts a [VcsCommitMetadata] to [VcsFullCommitDetails] by delegating all metadata fields and
 * returning **precomputed** change collections. The changes are computed off the EDT in
 * [SaplingLogProvider.readFullDetails] and merely handed out here, because the platform calls
 * [getChanges] on the **EDT** while rendering — so this must never run `sl`.
 *
 * [changesByParent] key 0 = changes vs the first parent (the common `getChanges()`); additional keys
 * (only present for merge commits) = changes vs each further parent. `getChanges(parent)` falls back
 * to key 0 when a specific parent index was not precomputed.
 *
 * Delegates only to [VcsCommitMetadata] (which already extends VcsShortCommitDetails) to avoid the
 * duplicate-supertype issue from delegating both interfaces.
 */
internal class SaplingFullCommitDetails(
    private val meta: VcsCommitMetadata,
    private val changesByParent: Map<Int, List<Change>>,
) : VcsFullCommitDetails, VcsCommitMetadata by meta {
    override fun getChanges(): Collection<Change> = changesByParent[0] ?: emptyList()
    override fun getChanges(parent: Int): Collection<Change> =
        changesByParent[parent] ?: changesByParent[0] ?: emptyList()
}
