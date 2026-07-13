package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import io.github.pfeisa.sapling.status.parseSaplingStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path

/**
 * Builds an IDE [Change] for an ISL "Open Diff View" click, given the file's [status] under the
 * requested comparison [type]. Mirrors [statusEntryToChange]: revision content always flows
 * through [SaplingContentRevision], revision identity through [SaplingRevisionNumber].
 *
 * - `Uncommitted`: base `.` (current commit) vs the working copy on disk.
 * - `Commit`: base `<hash>^` (parent) vs `<hash>`.
 *
 * Returns null for unsupported comparison types, a `Commit` without a hash, or a status with
 * nothing to diff (CLEAN/IGNORED). See design spec §3.3 / §7.
 */
fun changeForComparison(
    type: String,
    hash: String?,
    status: SaplingStatusCode,
    copySource: String?,
    repoRoot: Path,
    relativePath: String,
    cli: SaplingCli = SaplingCli(),
): Change? {
    val rootStr = repoRoot.toString()

    fun filePathOf(relative: String) =
        VcsUtil.getFilePath(repoRoot.resolve(relative).toFile(), false)

    fun rev(relative: String, revset: String) =
        SaplingContentRevision(filePathOf(relative), SaplingRevisionNumber(revset), rootStr, relative, cli)

    return when (type) {
        "Uncommitted" -> when (status) {
            SaplingStatusCode.MODIFIED ->
                Change(rev(relativePath, "."), CurrentContentRevision(filePathOf(relativePath)))

            SaplingStatusCode.ADDED, SaplingStatusCode.UNTRACKED -> {
                val source = copySource
                if (source != null) {
                    Change(rev(source, "."), CurrentContentRevision(filePathOf(relativePath)))
                } else {
                    Change(null, CurrentContentRevision(filePathOf(relativePath)))
                }
            }

            SaplingStatusCode.REMOVED, SaplingStatusCode.MISSING ->
                Change(rev(relativePath, "."), null)

            else -> null
        }

        "Commit" -> {
            val h = hash ?: return null
            when (status) {
                SaplingStatusCode.MODIFIED ->
                    Change(rev(relativePath, "$h^"), rev(relativePath, h))

                SaplingStatusCode.ADDED -> {
                    val source = copySource
                    if (source != null) {
                        Change(rev(source, "$h^"), rev(relativePath, h))
                    } else {
                        Change(null, rev(relativePath, h))
                    }
                }

                SaplingStatusCode.REMOVED ->
                    Change(rev(relativePath, "$h^"), null)

                else -> null
            }
        }

        else -> null
    }
}

/**
 * Classifies [relativePath] under the requested comparison via one `sl status` call
 * (working-copy-vs-`.` for `Uncommitted`, `--change <hash>` for `Commit`). Off-EDT: calls
 * [SaplingCli]. Returns null if `sl` fails or the file is not in the status output.
 */
fun classifyForComparison(
    type: String,
    hash: String?,
    repoRoot: Path,
    relativePath: String,
    cli: SaplingCli = SaplingCli(),
): SaplingStatusEntry? {
    val args = buildList {
        add("status"); add("-Tjson"); add("--copies")
        if (type == "Commit" && hash != null) {
            add("--change"); add(hash)
        }
        // `--` terminates option parsing: `relativePath` arrives from the untrusted ISL bridge,
        // so a leading-`-` name must never be parsed by `sl` as a flag.
        add("--"); add(relativePath)
    }
    val result = cli.run(repoRoot.toString(), args)
    if (!result.success) return null
    val entries = parseSaplingStatus(result.stdout)
    return entries.firstOrNull { it.path == relativePath } ?: entries.singleOrNull()
}
