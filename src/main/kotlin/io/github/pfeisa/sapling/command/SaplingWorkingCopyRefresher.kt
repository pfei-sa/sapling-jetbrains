package io.github.pfeisa.sapling.command

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

/**
 * Refreshes the IDE's view of the working copy: reloads changed file content into open editors
 * (async VFS refresh) and re-runs the change provider so gutters, status, and the diff baseline
 * recompute. Used after every mutating `sl` command and when focus leaves the ISL tool window.
 * Safe to call from the EDT.
 */
object SaplingWorkingCopyRefresher {

    fun refresh(project: Project, root: String) {
        LocalFileSystem.getInstance().findFileByPath(root)?.let {
            VfsUtil.markDirtyAndRefresh(true, true, true, it)
        }
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }
}
