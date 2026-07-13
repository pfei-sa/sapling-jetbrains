package io.github.pfeisa.sapling

import io.github.pfeisa.sapling.blame.SaplingAnnotationProvider
import io.github.pfeisa.sapling.changes.SaplingChangeProvider
import io.github.pfeisa.sapling.diff.SaplingDiffProvider
import io.github.pfeisa.sapling.history.SaplingHistoryProvider
import io.github.pfeisa.sapling.merge.SaplingMergeProvider
import io.github.pfeisa.sapling.rollback.SaplingRollbackEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
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

    override fun getType(): VcsType = VcsType.distributed

    override fun getChangeProvider(): ChangeProvider = _changeProvider

    override fun getDiffProvider(): DiffProvider = _diffProvider

    override fun getRollbackEnvironment(): RollbackEnvironment = _rollbackEnvironment

    // No CheckinEnvironment: committing is done through the embedded ISL, not the IDE commit UI.
    // (AbstractVcs.getCheckinEnvironment() defaults to null.)

    override fun getVcsHistoryProvider(): VcsHistoryProvider = _historyProvider

    override fun getAnnotationProvider(): AnnotationProvider = _annotationProvider

    override fun getMergeProvider(): MergeProvider = _mergeProvider
}
