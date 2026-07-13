package io.github.pfeisa.sapling.commit

/**
 * Stateless rules deciding whether the IDE's native commit affordance should be suppressed for a
 * Sapling working copy (commit/amend belongs in the embedded ISL).
 *
 * The platform gates the inline commit box on the *commit mode*, which `CommitModeManager` derives
 * from every active VCS's `AbstractVcs.getType()`: if all active VCSs are `distributed` the mode is
 * non-modal (inline commit box shown), otherwise modal (no inline box). Reporting Sapling as
 * `centralized` therefore forces modal mode — but only safely when Sapling is the *sole* active VCS,
 * so a co-mapped Git root is never dragged into modal commit.
 */
object SaplingCommitUiSuppression {

    /** True iff there is at least one active VCS and every active VCS is Sapling. */
    fun isSoleActiveVcs(activeVcsNames: List<String>, saplingVcsName: String): Boolean =
        activeVcsNames.isNotEmpty() && activeVcsNames.all { it == saplingVcsName }

    /** True iff the IDE should be forced into modal commit mode (hiding the inline commit box). */
    fun shouldForceModalCommit(hideCommitUi: Boolean, saplingIsSoleActiveVcs: Boolean): Boolean =
        hideCommitUi && saplingIsSoleActiveVcs
}
