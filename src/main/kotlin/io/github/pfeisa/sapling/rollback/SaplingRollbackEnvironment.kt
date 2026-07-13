package io.github.pfeisa.sapling.rollback

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.rollback.RollbackProgressListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * "Revert" for Sapling working-copy changes, backed by `sl revert --no-backup`. Wired via
 * [io.github.pfeisa.sapling.SaplingVcs.getRollbackEnvironment]. The platform invokes these
 * methods off the EDT and collects failures into the supplied exception lists.
 */
class SaplingRollbackEnvironment(private val project: Project, private val cli: SaplingCli = SaplingCli()) : RollbackEnvironment {

    override fun getRollbackOperationName(): String = "Revert"

    override fun rollbackChanges(
        changes: MutableList<out Change>,
        vcsExceptions: MutableList<VcsException>,
        listener: RollbackProgressListener,
    ) {
        // Prefer the after-path (current); fall back to before-path for deletions.
        val paths = changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
        revert(paths, vcsExceptions, listener)
    }

    override fun rollbackMissingFileDeletion(
        files: MutableList<out FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        revert(files, exceptions, listener)
    }

    override fun rollbackModifiedWithoutCheckout(
        files: MutableList<out VirtualFile>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        // Sapling is a DVCS with no lock/checkout model — nothing to do.
    }

    override fun rollbackIfUnchanged(file: VirtualFile) {
        // Optional optimization; not needed.
    }

    private fun revert(
        files: List<FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        // Group by repo root so a multi-root selection reverts against the correct root.
        val byRoot: Map<Path?, List<FilePath>> = files.groupBy { SaplingPaths.repoRoot(it.ioFile.toPath()) }
        for ((root, group) in byRoot) {
            if (root == null) continue
            val rels = group.mapNotNull { SaplingPaths.relative(root, it.ioFile.toPath()) }
            if (rels.isEmpty()) continue
            val result = cli.run(root.toString(), revertArgs(rels))
            if (result.success) {
                group.forEach { listener.accept(it) }
            } else {
                exceptions.add(VcsException("sl revert failed: ${result.stderr}"))
            }
            LocalFileSystem.getInstance().findFileByPath(root.toString())?.let {
                VfsUtil.markDirtyAndRefresh(false, true, true, it)
            }
        }
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }
}
