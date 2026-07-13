package io.github.pfeisa.sapling.history

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.log.parseSaplingLog
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile

/** Per-file history via `sl log -Tjson <file>`. */
class SaplingHistoryProvider(
    private val project: Project,
    private val cli: SaplingCli = SaplingCli(),
) : VcsHistoryProvider {

    override fun getUICustomization(session: VcsHistorySession, forShortcutRegistration: javax.swing.JComponent) = null
    override fun getAdditionalActions(refresher: Runnable) = emptyArray<com.intellij.openapi.actionSystem.AnAction>()
    override fun isDateOmittable(): Boolean = false
    override fun getHelpId(): String? = null
    override fun supportsHistoryForDirectories(): Boolean = false
    override fun getHistoryDiffHandler() = null
    override fun canShowHistoryFor(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val nio = SaplingPaths.nioPathOrNull(file) ?: return false
        return SaplingPaths.repoRoot(nio) != null
    }

    override fun createSessionFor(filePath: FilePath): VcsHistorySession? {
        val revisions = loadHistory(filePath)
        val current = revisions.firstOrNull()?.revisionNumber
        return object : VcsAbstractHistorySession(revisions, current) {
            override fun calcCurrentRevisionNumber(): VcsRevisionNumber? = current
            override fun copy(): VcsHistorySession = createSessionFor(filePath)!!
        }
    }

    override fun reportAppendableHistory(path: FilePath, partner: VcsAppendableHistorySessionPartner) {
        val session = createSessionFor(path) ?: return
        partner.reportCreatedEmptySession(session as VcsAbstractHistorySession)
    }

    private fun loadHistory(filePath: FilePath): List<VcsFileRevision> {
        // Derive the nio path from the FilePath's own IO file (Windows-correct), not
        // Paths.get(filePath.path) on the normalized IDE path string.
        val nio = filePath.ioFile.toPath()
        val root = SaplingPaths.repoRoot(nio) ?: return emptyList()
        val relative = SaplingPaths.relative(root, nio) ?: return emptyList()
        // `--` terminates option parsing so a file named like a flag can't inject `sl` options.
        val result = cli.run(root.toString(), listOf("log", "-Tjson", "-f", "--", relative))
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        return parseSaplingLog(result.stdout).map { entry ->
            SaplingFileRevision(
                node = entry.node,
                author = entry.author,
                dateEpochSeconds = entry.dateEpochSeconds,
                message = entry.description,
                repoRoot = root.toString(),
                relativePath = relative,
                cli = cli,
            )
        }
    }
}
