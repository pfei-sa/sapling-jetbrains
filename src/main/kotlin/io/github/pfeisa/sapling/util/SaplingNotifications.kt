package io.github.pfeisa.sapling.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Shared balloon notifications (group id "Sapling", registered in plugin.xml). */
object SaplingNotifications {
    fun info(project: Project, message: String) = notify(project, message, NotificationType.INFORMATION)
    fun warn(project: Project, message: String) = notify(project, message, NotificationType.WARNING)
    fun error(project: Project, message: String) = notify(project, message, NotificationType.ERROR)

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sapling")
            .createNotification(message, type)
            .notify(project)
    }
}
