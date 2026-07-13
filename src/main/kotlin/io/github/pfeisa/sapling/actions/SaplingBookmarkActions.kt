package io.github.pfeisa.sapling.actions

import io.github.pfeisa.sapling.command.SaplingCommandRunner
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

class SaplingCreateBookmarkAction : DumbAwareAction("Create Bookmark…") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val name = Messages.showInputDialog(project, "Bookmark name:", "Create Bookmark", null) ?: return
        if (name.isBlank()) return
        // `--` so a name beginning with `-` can't be read by sl as an option.
        SaplingCommandRunner.run(project, "sl bookmark", listOf("bookmark", "--", name))
    }
}

class SaplingDeleteBookmarkAction : DumbAwareAction("Delete Bookmark…") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val name = Messages.showInputDialog(project, "Bookmark to delete:", "Delete Bookmark", null) ?: return
        if (name.isBlank()) return
        val choice = Messages.showOkCancelDialog(
            project,
            "Delete bookmark \"$name\"? This cannot be undone.",
            "Delete Bookmark",
            "Delete",
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        )
        if (choice != Messages.OK) return
        SaplingCommandRunner.run(project, "sl bookmark -d", listOf("bookmark", "-d", "--", name))
    }
}
