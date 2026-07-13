package io.github.pfeisa.sapling.startup

import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import io.github.pfeisa.sapling.settings.SaplingSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths

/**
 * When the "auto-open ISL" setting is enabled and the project is a Sapling repo,
 * activate the ISL tool window on project open. Default off.
 */
class SaplingAutoOpenIsl : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!SaplingSettings.getInstance().autoOpenIsl) return
        val base = project.basePath ?: return
        // Match ISL availability (both `.sl` and dotgit modes), not just `.sl` roots.
        if (SaplingRepoDetector.findWorkingCopyRoot(Paths.get(base)) == null) return
        withContext(Dispatchers.EDT) {
            ToolWindowManager.getInstance(project).getToolWindow(ISL_TOOL_WINDOW_ID)?.activate(null)
        }
    }

    private companion object {
        const val ISL_TOOL_WINDOW_ID = "Sapling ISL"
    }
}
