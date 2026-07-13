package io.github.pfeisa.sapling.widget

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.command.SaplingCommandRunner
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import java.nio.file.Paths

/**
 * Status-bar indicator for Sapling's current commit `.` (active bookmark, else short hash +
 * summary). Clicking opens a popup: bookmarks (→ `sl goto`), plus New/Delete Bookmark and Goto.
 */
class SaplingBookmarkWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    @Volatile private var labelText: String = "Sapling"
    @Volatile private var bookmarks: List<SaplingBookmark> = emptyList()
    @Volatile private var disposed: Boolean = false
    // EDT-only: written in install()/dispose() and read in getPopup()/onSuccess(), all on the EDT.
    private var statusBar: StatusBar? = null

    override fun ID(): String = "Sapling.CommitWidget"

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        refresh()
    }

    override fun dispose() {
        disposed = true
        statusBar = null
    }

    // --- MultipleTextValuesPresentation (called on EDT; read cached fields only) ---

    override fun getSelectedValue(): String = labelText

    override fun getTooltipText(): String = "Sapling: current commit and bookmarks"

    override fun getPopup(): JBPopup? {
        val bar = statusBar ?: return null
        val group = DefaultActionGroup()
        for (b in bookmarks) {
            val title = if (b.active) "● ${b.name}" else b.name
            group.add(object : DumbAwareAction(title) {
                override fun actionPerformed(e: AnActionEvent) {
                    // Passed as the value of `-r`, so a bookmark starting with `-` is not read as an option.
                    SaplingCommandRunner.run(project, "sl goto ${b.name}", listOf("goto", "-r", b.name)) {
                        refresh()
                    }
                }
            })
        }
        group.addSeparator()
        ActionManager.getInstance().getAction("Sapling.CreateBookmark")?.let { group.add(it) }
        ActionManager.getInstance().getAction("Sapling.DeleteBookmark")?.let { group.add(it) }
        ActionManager.getInstance().getAction("Sapling.Goto")?.let { group.add(it) }

        val context = DataManager.getInstance().getDataContext(bar.component)
        return JBPopupFactory.getInstance().createActionGroupPopup(
            "Sapling",
            group,
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
        )
    }

    /** Re-reads `.` off the EDT and refreshes the widget text. Safe to call repeatedly. */
    fun refresh() {
        val base = project.basePath ?: return
        object : Task.Backgroundable(project, "Sapling: reading current commit", false) {
            private var newLabel = "Sapling"
            private var newBookmarks: List<SaplingBookmark> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                val root = SaplingPaths.repoRoot(Paths.get(base))?.toString() ?: return
                val cli = SaplingCli()
                newBookmarks = parseBookmarkList(cli.run(root, listOf("bookmark", "-Tjson"), indicator).stdout)
                val active = newBookmarks.firstOrNull { it.active }?.name
                // Newline-separated template (avoids embedding a NUL in argv).
                val logOut = cli.run(root, listOf("log", "-r", ".", "-T", "{node|short}\n{desc|firstline}\n"), indicator).stdout
                val parts = logOut.split('\n')
                val shortNode = parts.getOrNull(0)?.trim().orEmpty()
                val summary = parts.getOrNull(1)?.trim().orEmpty()
                newLabel = if (shortNode.isEmpty()) "Sapling" else commitLabel(active, shortNode, summary)
            }

            override fun onSuccess() {
                if (disposed) return
                labelText = newLabel
                bookmarks = newBookmarks
                statusBar?.updateWidget(ID())
            }
        }.queue()
    }
}
