package io.github.pfeisa.sapling.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogDataKeys
import java.awt.datatransfer.StringSelection

/** Copies the selected commit's Sapling hash from the VCS Log context menu. */
class CopyCommitHashAction : DumbAwareAction("Copy Commit Hash") {

    override fun actionPerformed(e: AnActionEvent) {
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
        val hashes = selection.commits.joinToString("\n") { it.hash.asString() }
        if (hashes.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(hashes))
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.isNotEmpty() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
