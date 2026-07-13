package io.github.pfeisa.sapling.diff

import io.github.pfeisa.sapling.changes.SaplingContentRevision
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

/** Supplies content-at-revision so the IDE's native diff viewer can compare files. */
class SaplingDiffProvider(
    private val project: Project,
    private val cli: SaplingCli = SaplingCli(),
) : DiffProvider {

    // Head revision is derived purely from the path (repo root + `sl log -r .`); it needs
    // no live VirtualFile, so deleted/renamed FilePaths still resolve.
    private fun headRevision(nioPath: java.nio.file.Path): SaplingRevisionNumber? {
        val root = SaplingPaths.repoRoot(nioPath) ?: return null
        val result = cli.run(root.toString(), listOf("log", "-r", ".", "-T", "{node}\n"))
        val node = result.stdout.trim()
        return if (result.success && node.isNotEmpty()) SaplingRevisionNumber(node) else null
    }

    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? = headRevision(file.toNioPath())

    override fun getLastRevision(virtualFile: VirtualFile): ItemLatestState? {
        val rev = headRevision(virtualFile.toNioPath()) ?: return null
        return ItemLatestState(rev, true, true)
    }

    override fun getLastRevision(filePath: FilePath): ItemLatestState? {
        val rev = headRevision(filePath.ioFile.toPath()) ?: return null
        return ItemLatestState(rev, true, true)
    }

    override fun getLatestCommittedRevision(vcsRoot: VirtualFile): VcsRevisionNumber? = null

    override fun createFileContent(revisionNumber: VcsRevisionNumber, selectedFile: VirtualFile): ContentRevision? {
        val root = SaplingPaths.repoRoot(selectedFile.toNioPath()) ?: return null
        val relative = SaplingPaths.relative(root, selectedFile.toNioPath()) ?: return null
        val rev = revisionNumber as? SaplingRevisionNumber ?: SaplingRevisionNumber(revisionNumber.asString())
        return SaplingContentRevision(VcsUtil.getFilePath(selectedFile), rev, root.toString(), relative, cli)
    }
}
