package io.github.pfeisa.sapling.isl

import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import io.github.pfeisa.sapling.util.SaplingNotifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import java.nio.file.Paths

class IslToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean {
        val base = project.basePath ?: return false
        // Available in both `.sl` and dotgit (Git-backed) modes — `sl web` runs in either.
        return SaplingRepoDetector.findWorkingCopyRoot(Paths.get(base)) != null
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val base = project.basePath
        val root = base?.let { SaplingRepoDetector.findWorkingCopyRoot(Paths.get(it)) }?.toString()
        val contentFactory = ContentFactory.getInstance()

        if (root == null) {
            val content = contentFactory.createContent(JBLabel("No Sapling or Git repository found."), "", false)
            toolWindow.contentManager.addContent(content)
            return
        }

        val panel = IslBrowserPanel(project, root)
        Disposer.register(toolWindow.disposable, panel)

        val content = contentFactory.createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)

        // Refresh the editor + VCS state when focus leaves ISL, so goto/revert/commit done inside
        // the webview don't leave stale file content or change-gutter colors behind. Scoped to the
        // tool window's lifecycle via its disposable (no static state, no Application/Project parent).
        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            IslFocusRefreshListener(project, toolWindow.id, root),
        )

        val job = project.service<IslServerManager>().launchAsync(root) { result ->
            when (result) {
                is IslLaunchResult.Ready -> panel.load(result.info)
                is IslLaunchResult.Failed ->
                    SaplingNotifications.error(project, "Could not start ISL: ${result.error}")
            }
        }
        // Closing the tool window disposes the panel; cancel the in-flight launch coroutine.
        Disposer.register(panel, Disposable { job.cancel() })
    }
}
