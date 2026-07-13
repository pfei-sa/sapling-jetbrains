package io.github.pfeisa.sapling.merge

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Backs the IDE 3-way merge dialog for Sapling conflicts. base = ancestor(p1,p2),
 * CURRENT = local (p1 / "yours"), LAST = other (p2 / "theirs"), each via `sl cat`.
 * `sl resolve --mark` marks a file resolved. Continuing/committing the merge stays in ISL.
 * Platform-invoked off the EDT.
 */
class SaplingMergeProvider(@Suppress("unused") private val project: Project) : MergeProvider {

    private val cli = SaplingCli()

    @Throws(VcsException::class)
    override fun loadRevisions(file: VirtualFile): MergeData {
        val nio = file.toNioPath()
        val root = SaplingPaths.repoRoot(nio) ?: throw VcsException("Not in a Sapling repository: ${file.path}")
        val rootStr = root.toString()
        val relative = SaplingPaths.relative(root, nio) ?: throw VcsException("File is outside the repository: ${file.path}")

        val data = MergeData()
        data.ORIGINAL = catAtRevset(rootStr, "ancestor(p1(),p2())", relative)
        data.CURRENT = catAtRevset(rootStr, "p1()", relative)   // local / yours
        data.LAST = catAtRevset(rootStr, "p2()", relative)      // other / theirs
        return data
    }

    /** Bytes of [relative] at the commit named by [revset], or empty if that side has no such node/file. */
    private fun catAtRevset(root: String, revset: String, relative: String): ByteArray {
        val node = resolveNode(root, revset) ?: return ByteArray(0)
        val result = cli.runForBytes(root, listOf("cat", "--rev", node, "--", relative))
        return when {
            result.success -> result.stdout
            // Infrastructure failure (hung / cancelled / couldn't start `sl`) must NOT be silently
            // masked as "file absent on that side" — that would corrupt the merge dialog.
            result.timedOut || result.cancelled || result.exitCode == -1 ->
                throw VcsException("sl cat failed for $revset ($relative): sl timed out or could not start")
            // Ordinary non-zero exit = file genuinely not present at this revision (e.g. add/add) -> empty.
            else -> ByteArray(0)
        }
    }

    private fun resolveNode(root: String, revset: String): String? {
        val result = cli.run(root, listOf("log", "-r", revset, "-T", "{node}\n"))
        val node = result.stdout.trim()
        return if (result.success && node.isNotEmpty()) node else null
    }

    override fun conflictResolvedForFile(file: VirtualFile) {
        val nio = file.toNioPath()
        val root = SaplingPaths.repoRoot(nio) ?: return
        val relative = SaplingPaths.relative(root, nio) ?: return
        cli.run(root.toString(), listOf("resolve", "--mark", "--", relative))
    }

    override fun isBinary(file: VirtualFile): Boolean = file.fileType.isBinary
}
