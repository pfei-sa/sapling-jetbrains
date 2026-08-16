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
 * Base revset for each ISL comparison type that diffs against the live working copy, exactly as
 * the stock ISL client maps them (see design spec §2's recovered table).
 */
private val WORKING_COPY_COMPARISON_BASES = mapOf(
    "Uncommitted" to ".",
    "Head" to ".^",
    "Stack" to "ancestor(.,interestingmaster())",
)

/** True for the comparison types [changeForComparison] can turn into an IDE diff. */
fun isComparisonTypeSupported(type: String): Boolean =
    type == "Commit" || type in WORKING_COPY_COMPARISON_BASES

/**
 * The `sl status` invocation that classifies a file under the requested comparison [type]:
 * plain status for `Uncommitted`, `--rev <base>` for `Head`/`Stack`, `--change <hash>` for
 * `Commit`. Returns null for unsupported types or a `Commit` without a hash (nothing to run).
 */
fun statusArgsForComparison(type: String, hash: String?, relativePath: String): List<String>? {
    val revArgs = when {
        type == "Uncommitted" -> emptyList()
        type in WORKING_COPY_COMPARISON_BASES -> listOf("--rev", WORKING_COPY_COMPARISON_BASES.getValue(type))
        type == "Commit" -> listOf("--change", hash ?: return null)
        else -> return null
    }
    // `--` terminates option parsing: `relativePath` arrives from the untrusted ISL bridge,
    // so a leading-`-` name must never be parsed by `sl` as a flag.
    return listOf("status", "-Tjson", "--copies") + revArgs + listOf("--", relativePath)
}

/**
 * Builds an IDE [Change] for an ISL "Open Diff View" click, given the file's [status] under the
 * requested comparison [type]. Mirrors [statusEntryToChange]: revision content always flows
 * through [SaplingContentRevision], revision identity through [SaplingRevisionNumber].
 *
 * - `Uncommitted` / `Head` / `Stack`: base revset (`.` / `.^` / stack base) vs the working copy
 *   on disk.
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

    val workingCopyBase = WORKING_COPY_COMPARISON_BASES[type]
    return when {
        workingCopyBase != null -> when (status) {
            SaplingStatusCode.MODIFIED ->
                Change(rev(relativePath, workingCopyBase), CurrentContentRevision(filePathOf(relativePath)))

            SaplingStatusCode.ADDED, SaplingStatusCode.UNTRACKED -> {
                val source = copySource
                if (source != null) {
                    Change(rev(source, workingCopyBase), CurrentContentRevision(filePathOf(relativePath)))
                } else {
                    Change(null, CurrentContentRevision(filePathOf(relativePath)))
                }
            }

            SaplingStatusCode.REMOVED, SaplingStatusCode.MISSING ->
                Change(rev(relativePath, workingCopyBase), null)

            else -> null
        }

        type == "Commit" -> {
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
 * Classifies [relativePath] under the requested comparison via one `sl status` call (see
 * [statusArgsForComparison] for the per-type invocation). Off-EDT: calls [SaplingCli].
 * Returns null for unsupported comparisons, if `sl` fails, or the file is not in the output.
 */
fun classifyForComparison(
    type: String,
    hash: String?,
    repoRoot: Path,
    relativePath: String,
    cli: SaplingCli = SaplingCli(),
): SaplingStatusEntry? {
    val args = statusArgsForComparison(type, hash, relativePath) ?: return null
    val result = cli.run(repoRoot.toString(), args)
    if (!result.success) return null
    val entries = parseSaplingStatus(result.stdout)
    return entries.firstOrNull { it.path == relativePath } ?: entries.singleOrNull()
}
