package io.github.pfeisa.sapling.isl

import io.github.pfeisa.sapling.util.SaplingNotifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

class RestartIslServerAction : DumbAwareAction("Restart ISL Server") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<IslServerManager>().stop()
        SaplingNotifications.info(project, "ISL server stopped — reopen the Sapling ISL tool window to restart.")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
