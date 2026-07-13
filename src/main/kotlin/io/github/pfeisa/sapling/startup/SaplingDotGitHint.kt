package io.github.pfeisa.sapling.startup

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import io.github.pfeisa.sapling.util.SaplingNotifications
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

/**
 * If the project is a Git-backed Sapling repo (dotgit mode) that isn't a `.sl` root,
 * hint that Sapling can be mapped manually. dotgit defaults to Git4Idea (spec §4).
 */
class SaplingDotGitHint : ProjectActivity {
    override suspend fun execute(project: Project) {
        val props = PropertiesComponent.getInstance(project)
        if (props.getBoolean(HINT_SHOWN_KEY)) return                   // shown once already — don't nag
        val base = project.basePath ?: return
        val root = Paths.get(base)
        if (SaplingRepoDetector.isSaplingRoot(root)) return           // .sl auto-detected already
        if (!Files.isDirectory(root.resolve(".git"))) return
        // Blocking subprocess I/O belongs on IO, not the CPU-bound Default dispatcher.
        val slRoot = withContext(Dispatchers.IO) { SaplingCli().run(base, listOf("root")) }
        // Mark the probe done regardless of outcome: this is a one-time hint, so we must not
        // re-launch `sl root` on every project open when sl is absent or this isn't a Sapling repo.
        props.setValue(HINT_SHOWN_KEY, true)
        if (slRoot.success) {
            SaplingNotifications.info(
                project,
                "This looks like a Git-backed Sapling repo. To use Sapling here, map it under " +
                    "Settings → Version Control → Directory Mappings.",
            )
        }
    }

    private companion object {
        const val HINT_SHOWN_KEY = "io.github.pfeisa.sapling.dotgit.hint.shown"
    }
}
