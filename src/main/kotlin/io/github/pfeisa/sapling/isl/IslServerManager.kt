package io.github.pfeisa.sapling.isl

import io.github.pfeisa.sapling.cli.SaplingCli
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the `sl web` lifecycle for a project. All process work runs on the injected
 * service coroutine scope; results are delivered back on the EDT.
 */
@Service(Service.Level.PROJECT)
class IslServerManager(
    private val project: Project,
    private val cs: CoroutineScope,
) : Disposable {

    companion object {
        private val LOG = logger<IslServerManager>()
    }

    private val cli = SaplingCli()

    @Volatile
    private var info: IslServerInfo? = null

    // The root `sl web` was launched from (may differ from project.basePath when the
    // project is opened at a subdirectory); reused as the cwd for --kill.
    @Volatile
    private var launchRoot: String? = null

    fun currentInfo(): IslServerInfo? = info

    /** Launches (or reuses) the ISL server for [root]; [onResult] is invoked on the EDT. */
    fun launchAsync(root: String, onResult: (IslLaunchResult) -> Unit): Job {
        return cs.launch(Dispatchers.IO) {
            // Bridge coroutine cancellation to the blocking process: cancelling the indicator
            // makes CapturingProcessHandler destroy `sl web` promptly instead of letting it
            // linger to the timeout when the tool window is closed mid-launch.
            val indicator = EmptyProgressIndicator()
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    indicator.cancel()
                }
            }
            try {
                val result = cli.run(
                    root,
                    listOf("web", "--platform", "androidStudio", "--json", "--no-open", "--cwd", root),
                    indicator = indicator,
                    timeoutMs = 60_000,
                )
                val parsed =
                    if (!result.success) {
                        // Never surface raw `sl web` output — it can carry the token-bearing URL.
                        LOG.warn("sl web failed (exit ${result.exitCode}): ${scrubSlWebOutput(result.stderr)}")
                        IslLaunchResult.Failed("sl web exited with code ${result.exitCode}")
                    } else {
                        parseIslLaunch(result.stdout.trim())
                    }
                // isActive guards against a dispose() that cancelled the scope while the
                // blocking `sl web` call was in flight — don't write state onto a disposed instance.
                if (parsed is IslLaunchResult.Ready && isActive) {
                    info = parsed.info
                    launchRoot = root
                }
                withContext(Dispatchers.EDT) { onResult(parsed) }
            } finally {
                watcher.cancel()
            }
        }
    }

    /** Best-effort stop of the running server (used by the Restart action). */
    fun stop() {
        val current = info ?: return
        val root = launchRoot ?: project.basePath
        if (root == null) {
            LOG.warn("Cannot stop ISL server: launch root unavailable")
            return
        }
        info = null
        launchRoot = null
        cs.launch(Dispatchers.IO) {
            cli.run(root, listOf("web", "--kill", "-p", current.port.toString(), "--cwd", root))
        }
    }

    override fun dispose() {
        // No --persist was passed, so the server auto-shuts-down when idle.
        info = null
        launchRoot = null
    }
}
