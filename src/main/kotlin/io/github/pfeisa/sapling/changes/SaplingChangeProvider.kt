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
import java.nio.file.Paths

/**
 * Reports working-copy changes to the IDE. Runs off the EDT and issues exactly ONE
 * `sl status` per invocation (never per-file, never a full rescan of unchanged files).
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

                SaplingStatusCode.IGNORED ->
                    // Dormant unless `sl status` is invoked with -i/--ignored; the handler is wired for that future flag.
                    builder.processIgnoredFile(VcsUtil.getFilePath(nio.toFile(), false))

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
}
