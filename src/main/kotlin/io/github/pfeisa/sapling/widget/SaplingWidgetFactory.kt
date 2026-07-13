package io.github.pfeisa.sapling.widget

import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.nio.file.Paths

/** Registers the Sapling commit/bookmark status-bar widget. `getId()` matches the plugin.xml id. */
class SaplingWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "Sapling.CommitWidget"

    override fun getDisplayName(): String = "Sapling Commit"

    override fun isAvailable(project: Project): Boolean {
        val base = project.basePath ?: return false
        return SaplingPaths.repoRoot(Paths.get(base)) != null
    }

    override fun createWidget(project: Project): StatusBarWidget = SaplingBookmarkWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
