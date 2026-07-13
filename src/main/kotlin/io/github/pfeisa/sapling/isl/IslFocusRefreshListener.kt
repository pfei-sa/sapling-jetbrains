package io.github.pfeisa.sapling.isl

import io.github.pfeisa.sapling.command.SaplingWorkingCopyRefresher
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * Refreshes the IDE's VFS + VCS state when focus leaves the ISL tool window. Operations run inside
 * the ISL webview (goto/revert/commit) mutate the working copy in the separate `sl web` process and
 * never touch [SaplingWorkingCopyRefresher] via [io.github.pfeisa.sapling.command.SaplingCommandRunner];
 * without this, editor content and change-gutter colors stay stale until the next IDE-native action.
 * Refresh timing is "when the user looks back at the editor" — see the design spec.
 */
class IslFocusRefreshListener(
    private val project: Project,
    private val islToolWindowId: String,
    private val repoRoot: String,
) : ToolWindowManagerListener {

    // Seed with the currently-active window so the very first "leave ISL" transition is caught.
    private var previousActiveId: String? = ToolWindowManager.getInstance(project).activeToolWindowId

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val current = toolWindowManager.activeToolWindowId
        val shouldRefresh =
            IslRefreshDecision.shouldRefreshOnStateChange(previousActiveId, current, islToolWindowId)
        previousActiveId = current
        if (shouldRefresh) {
            SaplingWorkingCopyRefresher.refresh(project, repoRoot)
        }
    }
}
