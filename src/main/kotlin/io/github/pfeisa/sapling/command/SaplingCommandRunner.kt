package io.github.pfeisa.sapling.command

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.cli.SaplingResult
import io.github.pfeisa.sapling.util.SaplingNotifications
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/** Runs one `sl` subcommand as a cancellable background task, then refreshes + notifies. */
object SaplingCommandRunner {

    fun run(
        project: Project,
        title: String,
        args: List<String>,
        onSuccess: (SaplingResult) -> Unit = {},
    ) {
        object : Task.Backgroundable(project, title, true) {
            private var root: String? = null
            private var result: SaplingResult? = null

            override fun run(indicator: ProgressIndicator) {
                // Resolve the repo root off the EDT (findRepoRoot is a filesystem walk).
                val base = project.basePath ?: return
                val resolved = SaplingPaths.repoRoot(Paths.get(base))?.toString() ?: return
                root = resolved
                result = SaplingCli().run(resolved, args, indicator)
            }

            override fun onSuccess() {
                val res = result
                val resolvedRoot = root
                if (res == null || resolvedRoot == null) {
                    SaplingNotifications.error(project, "$title failed: not inside a Sapling repository")
                    return
                }
                if (res.cancelled) return // user-initiated cancel — no notification
                if (res.success) {
                    SaplingWorkingCopyRefresher.refresh(project, resolvedRoot)
                    SaplingNotifications.info(project, "$title finished")
                    onSuccess(res)
                } else if (res.timedOut) {
                    SaplingNotifications.error(project, "$title timed out after 30s")
                } else {
                    SaplingNotifications.error(project, "$title failed: ${res.stderr}")
                }
            }

            override fun onThrowable(error: Throwable) {
                // ProcessCanceledException routes to onCancel, not here; surface real errors
                // instead of the framework's silent event-log entry.
                SaplingNotifications.error(project, "$title failed: ${error.message ?: error.javaClass.simpleName}")
            }
        }.queue()
    }
}
