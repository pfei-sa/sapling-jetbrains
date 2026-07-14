package io.github.pfeisa.sapling.blame

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogContentUtil
import java.nio.file.Path

/**
 * Editor-gutter → VCS navigation for blame. UI actions (run on the EDT); NOT `sl` calls.
 * The VCS-Log classes are available via the declared bundledModule("intellij.platform.vcs.log.impl").
 */
object SaplingAnnotationNavigator {
    private val LOG = logger<SaplingAnnotationNavigator>()

    /** Open/activate the repo-wide Log tool window and select [hash]. No-op (logged) if the log can't resolve the root. */
    fun showInLog(project: Project, repoRoot: Path, hash: String) {
        val rootVf = LocalFileSystem.getInstance().findFileByNioFile(repoRoot)
        if (rootVf == null) {
            LOG.warn("Cannot resolve repo root VirtualFile for $repoRoot; skipping Show in Log")
            return
        }
        // runInMainLog opens the Log tool window and invokes the consumer once the UI is ready.
        VcsLogContentUtil.runInMainLog(project) { logUi ->
            logUi.vcsLog.jumpToCommit(HashImpl.build(hash), rootVf)
        }
    }

    /** Open the per-file History tab, reusing SaplingHistoryProvider via the registered "Sapling" VCS. */
    fun showFileHistory(project: Project, filePath: FilePath) {
        val vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName("Sapling") ?: return
        val historyProvider = vcs.vcsHistoryProvider ?: return
        AbstractVcsHelper.getInstance(project).showFileHistory(historyProvider, filePath, vcs)
    }
}
