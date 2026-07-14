package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.history.SaplingFileRevision
import io.github.pfeisa.sapling.log.SaplingLogEntry
import io.github.pfeisa.sapling.log.parseSaplingLog
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

// `--` terminates option parsing so a file named like a flag can't inject `sl` options.
internal fun buildAnnotateArgs(rev: String?, relative: String): List<String> =
    buildList {
        add("annotate"); add("-Tjson")
        if (rev != null) { add("--rev"); add(rev) }
        add("--"); add(relative)
    }

// Repeated `-r` flags are unioned by `sl log` — one JSON entry per distinct node.
internal fun buildLogArgs(nodes: List<String>): List<String> =
    buildList {
        add("log"); add("-Tjson")
        for (n in nodes) { add("-r"); add(n) }
    }

class SaplingAnnotationProvider(
    private val project: Project,
    private val cli: SaplingCli = SaplingCli(),
) : AnnotationProvider {

    companion object {
        private val LOG = logger<SaplingAnnotationProvider>()
    }

    override fun annotate(file: VirtualFile): FileAnnotation = doAnnotate(file, rev = null)

    override fun annotate(file: VirtualFile, revision: VcsFileRevision): FileAnnotation =
        doAnnotate(file, rev = revision.revisionNumber.asString())

    private fun doAnnotate(file: VirtualFile, rev: String?): FileAnnotation {
        val root = SaplingPaths.repoRoot(file.toNioPath())
            ?: throw VcsException("Not inside a Sapling repository: ${file.path}")
        val relative = SaplingPaths.relative(root, file.toNioPath())
            ?: throw VcsException("File is outside the repository: ${file.path}")

        val annotateResult = cli.run(root.toString(), buildAnnotateArgs(rev, relative))
        if (!annotateResult.success) throw VcsException("sl annotate failed: ${annotateResult.stderr}")
        val lines = parseSaplingAnnotate(annotateResult.stdout)

        val logEntries = fetchCommitMetadata(root.toString(), lines)
        val commits = buildBlameCommits(lines, logEntries)
        val revisions: List<VcsFileRevision> = commits.map { c ->
            SaplingFileRevision(c.node, c.author, c.dateEpochSeconds, c.message, root.toString(), relative, cli)
        }

        val currentRevision = if (rev != null) SaplingRevisionNumber(rev) else currentWorkingCopyRevision(root)
        return SaplingFileAnnotation(project, file, lines, currentRevision, commits, revisions, root)
    }

    /**
     * Best-effort batched `sl log` over the distinct nodes for tooltip + getRevisions metadata.
     * Purely additive: on failure it logs and returns empty, and blame still works from annotate data.
     * ProcessCanceledException is never swallowed.
     */
    private fun fetchCommitMetadata(rootStr: String, lines: List<SaplingBlameLine>): List<SaplingLogEntry> {
        val nodes = lines.mapNotNull { it.node.ifEmpty { null } }.distinct()
        if (nodes.isEmpty()) return emptyList()
        val result = cli.run(rootStr, buildLogArgs(nodes)) // rethrows ProcessCanceledException internally
        if (!result.success) {
            LOG.warn("sl log for blame metadata failed: ${result.stderr}")
            return emptyList()
        }
        return try {
            parseSaplingLog(result.stdout)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Failed to parse sl log for blame metadata", e)
            emptyList()
        }
    }

    private fun currentWorkingCopyRevision(root: Path): SaplingRevisionNumber? =
        cli.run(root.toString(), listOf("log", "-r", ".", "-T", "{node}\n"))
            .takeIf { it.success }?.stdout?.trim()?.ifEmpty { null }?.let { SaplingRevisionNumber(it) }
}
