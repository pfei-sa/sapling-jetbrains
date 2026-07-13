package io.github.pfeisa.sapling.changes

import com.intellij.openapi.vcs.history.VcsRevisionNumber

data class SaplingRevisionNumber(val hash: String) : VcsRevisionNumber {
    override fun asString(): String = hash

    /**
     * Stable *identity* ordering by hash string — consistent with [equals] and never
     * reports unequal revisions as equal (the old `else 0` did). NOTE: it is NOT
     * chronological or topological; a bare hash carries no such order without querying `sl`.
     */
    override fun compareTo(other: VcsRevisionNumber): Int = asString().compareTo(other.asString())
}
