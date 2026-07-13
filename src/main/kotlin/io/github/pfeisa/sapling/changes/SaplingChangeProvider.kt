package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.SaplingVcs
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.parseSaplingStatus
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.LocallyDeletedChange
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reports working-copy changes to the IDE. Runs off the EDT and issues a fixed, small number of `sl`
 * commands per invocation (never per-file, never a full rescan of unchanged files): one full-repo
 * `sl status` for changes, plus a best-effort `sl status -i --terse=i` to grey ignored files.
 */
class SaplingChangeProvider(
    private val project: Project,
    private val cli: SaplingCli = SaplingCli(),
) : ChangeProvider {

    companion object {
        private val LOG = logger<SaplingChangeProvider>()
    }

    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        val basePath = project.basePath ?: return
        // Working-copy detection (`.sl` or dotgit) so a Git-backed Sapling repo mapped to
        // Sapling still reports changes; `.sl`-only detection would return null in dotgit mode.
        val root = SaplingRepoDetector.findWorkingCopyRoot(Paths.get(basePath)) ?: return
        val rootStr = root.toString()

        // Grey ignored files (directory-collapsed, best-effort). Done first so the early returns below
        // (empty change set / unresolved parent) can never skip it — ignored dirs still grey in a repo
        // with no pending changes.
        reportIgnoredFiles(root, rootStr, builder, progress)

        val statusResult = cli.run(rootStr, listOf("status", "-Tjson", "--copies"), progress)
        // Convert a cancel into a PCE (matching resolveCurrentCommit); a normal `return` here would
        // report an empty change set and make the platform clear the whole Local Changes view.
        if (statusResult.cancelled) throw ProcessCanceledException()
        if (!statusResult.success) {
            throw VcsException("sl status failed: ${statusResult.stderr}")
        }

        // Drop the redundant `R old` half of a `sl mv old new` rename so it maps to a single move.
        val entries = suppressRenameSources(parseSaplingStatus(statusResult.stdout))
        if (entries.isEmpty()) return

        val parentRev = resolveCurrentCommit(rootStr, progress)
        if (parentRev == null) {
            LOG.warn("Could not resolve current commit (.) — skipping change reporting")
            return
        }

        for (entry in entries) {
            ProgressManager.checkCanceled()
            val nio = root.resolve(entry.path)
            when (entry.status) {
                SaplingStatusCode.UNTRACKED ->
                    builder.processUnversionedFile(VcsUtil.getFilePath(nio.toFile(), false))

                SaplingStatusCode.IGNORED -> {
                    // Ignored files are reported by reportIgnoredFiles() via a separate `-i --terse=i` call.
                    // The change query here has no `-i`, so this never fires — kept as an explicit no-op so an
                    // `I` entry can never fall through to the `else` branch and be mis-mapped to a Change.
                }

                SaplingStatusCode.MISSING ->
                    builder.processLocallyDeletedFile(
                        LocallyDeletedChange(
                            VcsUtil.getFilePath(nio.toFile(), false)
                        )
                    )

                SaplingStatusCode.CLEAN -> { /* no-op */ }

                else -> statusEntryToChange(entry, root, parentRev, cli)
                    ?.let { builder.processChange(it, SaplingVcs.KEY) }
            }
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true

    private fun resolveCurrentCommit(root: String, progress: ProgressIndicator): SaplingRevisionNumber? {
        val result = cli.run(root, listOf("log", "-r", ".", "-T", "{node}\n"), progress)
        if (result.cancelled) throw ProcessCanceledException()
        val node = result.stdout.trim()
        return if (result.success && node.isNotEmpty()) SaplingRevisionNumber(node) else null
    }

    /**
     * Reports ignored files/directories so the IDE greys them. Uses `--terse=i`, which collapses a
     * fully-ignored directory to a single entry (e.g. `build/`) — bounded by the number of top-level
     * ignored dirs, not the file count. `-i` shows ONLY ignored, so this MUST be its own call: appending
     * it to the change query would drop the actual changes.
     *
     * Best-effort: `--terse` is a hidden `sl` flag, so if the call (or parse) ever fails, greying is
     * skipped with a warning rather than breaking change reporting. Only cancellation propagates.
     */
    private fun reportIgnoredFiles(
        root: Path,
        rootStr: String,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
    ) {
        val result = cli.run(rootStr, listOf("status", "-i", "--terse=i", "-Tjson"), progress)
        if (result.cancelled) throw ProcessCanceledException()
        if (!result.success) {
            LOG.warn("sl status -i failed; ignored files will not be greyed: ${result.stderr}")
            return
        }
        val entries = try {
            parseSaplingStatus(result.stdout)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Could not parse ignored-file status; ignored files will not be greyed", e)
            return
        }
        for (entry in entries) {
            ProgressManager.checkCanceled()
            if (entry.status != SaplingStatusCode.IGNORED) continue
            val target = ignoredFileTarget(entry.path)
            val nio = root.resolve(target.relativePath)
            builder.processIgnoredFile(VcsUtil.getFilePath(nio.toFile(), target.isDirectory))
        }
    }
}

/** An ignored `sl status` path split into its repo-relative form and whether it denotes a directory. */
internal data class IgnoredFileTarget(val relativePath: String, val isDirectory: Boolean)

/**
 * Interprets a path from `sl status -i --terse=i`. `--terse=i` collapses a fully-ignored directory to one
 * entry with a trailing slash (e.g. `build/`, `docs/internal/`); a lone ignored file has none. The trailing
 * slash is the directory signal — strip it and remember it so a directory greys its whole subtree.
 */
internal fun ignoredFileTarget(path: String): IgnoredFileTarget =
    IgnoredFileTarget(path.trimEnd('/'), path.endsWith("/"))
