package io.github.pfeisa.sapling.actions

import io.github.pfeisa.sapling.command.SaplingCommandRunner
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

private fun runOn(e: AnActionEvent, title: String, args: List<String>) {
    val project = e.project ?: return
    SaplingCommandRunner.run(project, title, args)
}

class SaplingGotoAction : DumbAwareAction("Goto Commit…") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val rev = Messages.showInputDialog(project, "Revset to goto:", "Sapling Goto", null) ?: return
        if (rev.isBlank()) return
        SaplingCommandRunner.run(project, "sl goto", listOf("goto", "-r", rev))
    }
}

class SaplingPullAction : DumbAwareAction("Pull") {
    override fun actionPerformed(e: AnActionEvent) = runOn(e, "sl pull", listOf("pull"))
}

class SaplingPushAction : DumbAwareAction("Push") {
    override fun actionPerformed(e: AnActionEvent) = runOn(e, "sl push", listOf("push"))
}

class SaplingUncommitAction : DumbAwareAction("Uncommit") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val choice = Messages.showOkCancelDialog(
            project,
            "Uncommit the current commit? Its changes move back to the working copy.",
            "Sapling Uncommit",
            "Uncommit",
            Messages.getCancelButton(),
            Messages.getWarningIcon(),
        )
        if (choice != Messages.OK) return
        SaplingCommandRunner.run(project, "sl uncommit", listOf("uncommit"))
    }
}

class SaplingShelveAction : DumbAwareAction("Shelve") {
    override fun actionPerformed(e: AnActionEvent) = runOn(e, "sl shelve", listOf("shelve"))
}

class SaplingUnshelveAction : DumbAwareAction("Unshelve") {
    override fun actionPerformed(e: AnActionEvent) = runOn(e, "sl unshelve", listOf("unshelve"))
}
