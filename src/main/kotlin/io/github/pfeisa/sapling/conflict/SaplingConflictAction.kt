package io.github.pfeisa.sapling.conflict

import io.github.pfeisa.sapling.SaplingVcs
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingNotifications
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Paths

@Serializable
private data class ResolveEntry(val path: String = "", val status: String = "")

private val RESOLVE_JSON = Json { ignoreUnknownKeys = true }

/** Unresolved (status "U") paths from `sl resolve --list -Tjson`. Never throws. */
fun parseUnresolvedConflicts(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        RESOLVE_JSON.decodeFromString<List<ResolveEntry>>(json)
            .filter { it.status == "U" }
            .map { it.path }
    }.getOrDefault(emptyList())
}

/**
 * Opens the IDE 3-way merge dialog for the repo's unresolved conflicts, backed by
 * [io.github.pfeisa.sapling.merge.SaplingMergeProvider]. Marking a file resolved runs
 * `sl resolve --mark`; continuing/committing the merge is done in ISL.
 */
class SaplingResolveConflictsAction : DumbAwareAction("Resolve Conflicts…") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val base = project?.basePath
        e.presentation.isEnabledAndVisible = project != null && base != null &&
            SaplingPaths.repoRoot(Paths.get(base)) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val base = project.basePath ?: return
        val root = SaplingPaths.repoRoot(Paths.get(base))?.toString() ?: return
        object : Task.Backgroundable(project, "sl resolve --list", true) {
            private var unresolved: List<String> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                val json = SaplingCli().run(root, listOf("resolve", "--list", "-Tjson"), indicator).stdout
                unresolved = parseUnresolvedConflicts(json)
            }

            override fun onSuccess() {
                if (unresolved.isEmpty()) {
                    SaplingNotifications.info(project, "No unresolved conflicts")
                    return
                }
                val lfs = LocalFileSystem.getInstance()
                val files: List<VirtualFile> = unresolved.mapNotNull {
                    lfs.refreshAndFindFileByPath(Paths.get(root).resolve(it).toString())
                }
                if (files.isEmpty()) {
                    SaplingNotifications.warn(
                        project,
                        "Unresolved conflicts, but the files could not be located:\n" + unresolved.joinToString("\n"),
                    )
                    return
                }
                val vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(SaplingVcs.VCS_NAME) as? SaplingVcs
                val provider = vcs?.mergeProvider
                if (provider == null) {
                    SaplingNotifications.error(project, "Sapling merge provider unavailable")
                    return
                }
                AbstractVcsHelper.getInstance(project).showMergeDialog(files, provider)
            }
        }.queue()
    }
}
