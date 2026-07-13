package io.github.pfeisa.sapling

import io.github.pfeisa.sapling.blame.SaplingAnnotationProvider
import io.github.pfeisa.sapling.changes.SaplingChangeProvider
import io.github.pfeisa.sapling.commit.SaplingCommitUiSuppression
import io.github.pfeisa.sapling.diff.SaplingDiffProvider
import io.github.pfeisa.sapling.history.SaplingHistoryProvider
import io.github.pfeisa.sapling.merge.SaplingMergeProvider
import io.github.pfeisa.sapling.rollback.SaplingRollbackEnvironment
import io.github.pfeisa.sapling.settings.SaplingSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment

class SaplingVcs(project: Project) : AbstractVcs(project, VCS_NAME) {

    companion object {
        const val VCS_NAME = "Sapling"
        val KEY: VcsKey = createKey(VCS_NAME)
    }

    private val _changeProvider: ChangeProvider by lazy {
        SaplingChangeProvider(myProject)
    }

    private val _diffProvider: DiffProvider by lazy {
        SaplingDiffProvider(myProject)
    }

    private val _historyProvider: VcsHistoryProvider by lazy {
        SaplingHistoryProvider(myProject)
    }

    private val _annotationProvider: AnnotationProvider by lazy {
        SaplingAnnotationProvider(myProject)
    }

    private val _rollbackEnvironment: RollbackEnvironment by lazy {
        SaplingRollbackEnvironment(myProject)
    }

    private val _mergeProvider: MergeProvider by lazy {
        SaplingMergeProvider(myProject)
    }

    override fun getDisplayName(): String = "Sapling"

    // Reporting `centralized` forces the IDE into modal commit mode, which removes the inline commit
    // box — but only when Sapling is the sole active VCS and the user hasn't opted out, so a
    // co-mapped Git root keeps its own (non-modal) commit UI. `getAllActiveVcss()` returns a
    // materialized list and never calls back into `getType()`, so there is no recursion.
    override fun getType(): VcsType {
        val activeNames = ProjectLevelVcsManager.getInstance(myProject).allActiveVcss.map { it.name }
        val forceModal = SaplingCommitUiSuppression.shouldForceModalCommit(
            hideCommitUi = SaplingSettings.getInstance().hideCommitUi,
            saplingIsSoleActiveVcs = SaplingCommitUiSuppression.isSoleActiveVcs(activeNames, VCS_NAME),
        )
        return if (forceModal) VcsType.centralized else VcsType.distributed
    }

    override fun getChangeProvider(): ChangeProvider = _changeProvider

    override fun getDiffProvider(): DiffProvider = _diffProvider

    override fun getRollbackEnvironment(): RollbackEnvironment = _rollbackEnvironment

    // Committing/amending is done through the embedded ISL, not the IDE. Two things enforce that:
    //  1. No CheckinEnvironment (AbstractVcs.getCheckinEnvironment() defaults to null) — a commit
    //     would be a no-op. A null environment does NOT hide the commit UI on its own.
    //  2. When `hideCommitUi` is on (default), getType() forces modal commit mode (no inline commit
    //     box) and this flag grays the residual single Commit action (Ctrl+K, VCS-menu "Commit…",
    //     the Local Changes toolbar green-check). The platform only honors this flag for the
    //     single-VCS case, so a co-mapped Git root is unaffected.
    override fun isCommitActionDisabled(): Boolean = SaplingSettings.getInstance().hideCommitUi

    override fun getVcsHistoryProvider(): VcsHistoryProvider = _historyProvider

    override fun getAnnotationProvider(): AnnotationProvider = _annotationProvider

    override fun getMergeProvider(): MergeProvider = _mergeProvider
}
