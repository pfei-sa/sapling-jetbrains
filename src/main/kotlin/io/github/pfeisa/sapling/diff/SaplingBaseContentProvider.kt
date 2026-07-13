package io.github.pfeisa.sapling.diff

import io.github.pfeisa.sapling.changes.SaplingContentRevision
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.VcsBaseContentProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

/**
 * Feeds the editor-gutter LineStatusTracker with the committed base of a tracked file.
 * Registered via `com.intellij.vcs.baseContentProvider`. Called off the EDT by the platform.
 */
class SaplingBaseContentProvider(@Suppress("unused") private val project: Project) : VcsBaseContentProvider {
    private val cli = SaplingCli()

    override fun isSupported(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val nio = SaplingPaths.nioPathOrNull(file) ?: return false
        return SaplingPaths.repoRoot(nio) != null
    }

    override fun getBaseRevision(file: VirtualFile): VcsBaseContentProvider.BaseContent? {
        val nio = SaplingPaths.nioPathOrNull(file) ?: return null
        val root = SaplingPaths.repoRoot(nio) ?: return null
        val relative = SaplingPaths.relative(root, nio) ?: return null
        // Working-copy parent (`.`); no live VirtualFile needed.
        val result = cli.run(root.toString(), listOf("log", "-r", ".", "-T", "{node}\n"))
        val node = result.stdout.trim()
        if (!result.success || node.isEmpty()) return null
        val rev = SaplingRevisionNumber(node)
        val contentRevision = SaplingContentRevision(VcsUtil.getFilePath(file), rev, root.toString(), relative, cli)
        return SaplingBaseContent(rev, contentRevision)
    }

    /**
     * A null [loadContent] means "no committed base — do not track" (correct for an untracked
     * file, whose `sl cat` fails). This is the opposite of the diff path, where null would
     * corrupt a diff; here it is the intended signal.
     */
    private class SaplingBaseContent(
        private val rev: SaplingRevisionNumber,
        private val contentRevision: SaplingContentRevision,
    ) : VcsBaseContentProvider.BaseContent {
        override fun getRevisionNumber(): VcsRevisionNumber = rev
        override fun loadContent(): String? = try {
            contentRevision.content
        } catch (e: VcsException) {
            null
        }
    }
}
