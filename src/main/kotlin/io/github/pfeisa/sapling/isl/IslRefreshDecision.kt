package io.github.pfeisa.sapling.isl

/**
 * Stateless rule for the ISL focus-refresh listener: given the previously- and currently-active
 * tool window ids, decide whether focus just left the ISL tool window (and we should therefore
 * refresh the working copy). A `null` id means no tool window is active — typically the editor.
 */
object IslRefreshDecision {

    fun shouldRefreshOnStateChange(
        previousActiveId: String?,
        currentActiveId: String?,
        islToolWindowId: String,
    ): Boolean = previousActiveId == islToolWindowId && currentActiveId != islToolWindowId
}
