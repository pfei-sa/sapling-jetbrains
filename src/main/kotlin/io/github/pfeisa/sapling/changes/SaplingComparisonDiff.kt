package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import io.github.pfeisa.sapling.status.parseSaplingStatus
import io.github.pfeisa.sapling.util.resolveWithinRepoLexical
import io.github.pfeisa.sapling.util.resolveWithinRepoReal
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
 * The `sl status` invocation that classifies files under the requested comparison [type]:
 * plain status for `Uncommitted`, `--rev <base>` for `Head`/`Stack`, `--change <hash>` for
 * `Commit`. A null [relativePath] asks for the whole comparison rather than one file.
 * Returns null for unsupported types or a `Commit` without a hash (nothing to run).
 */
fun statusArgsForComparison(type: String, hash: String?, relativePath: String?): List<String>? {
    val revArgs = when {
        type == "Uncommitted" -> emptyList()
        type in WORKING_COPY_COMPARISON_BASES -> listOf("--rev", WORKING_COPY_COMPARISON_BASES.getValue(type))
        type == "Commit" -> listOf("--change", hash ?: return null)
        else -> return null
    }
    // `--` terminates option parsing: `relativePath` arrives from the untrusted ISL bridge,
    // so a leading-`-` name must never be parsed by `sl` as a flag.
    val pathArgs = if (relativePath == null) emptyList() else listOf("--", relativePath)
    return listOf("status", "-Tjson", "--copies") + revArgs + pathArgs
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

/**
 * One file of a comparison: its `sl status` path (and copy source, if the file was renamed) paired
 * with the IDE [Change] it maps to. The path is carried alongside so the file the user actually
 * clicked can still be located after unmappable entries have been dropped.
 */
data class ComparisonChange(val path: String, val copySource: String?, val change: Change)

/**
 * `Head` and `Stack` diff a base revset against the **working copy**, so an unscoped `sl status`
 * reports every untracked file in the tree on top of the commit's own files. ISL's file list for
 * those comparisons shows only committed files, so untracked entries are left out of the navigable
 * set — otherwise `.idea` files, build output and scratch files pad the diff viewer's next/previous
 * list with files the user never saw in ISL. `Uncommitted` keeps them: ISL's uncommitted list does
 * show untracked files, and clicking one is a real diff.
 *
 * This only narrows the *sibling* list. If a click ever does arrive for an untracked file under
 * `Head`/`Stack`, it simply won't be found in the list and the caller falls back to the
 * single-file diff — exactly the behaviour that shipped before navigation existed.
 */
private val COMPARISONS_EXCLUDING_UNTRACKED = setOf("Head", "Stack")

/**
 * Every file in the comparison, mapped to IDE [Change]s in `sl`'s output order — the whole set the
 * diff viewer needs in order to offer next/previous-file navigation. [suppressRenameSources] runs
 * first so an `sl mv` shows up as one moved file instead of a move plus a stray deletion (two
 * [Change]s sharing a before-path also trip the platform's duplicate-revision warning). Entries
 * with nothing to diff (CLEAN/IGNORED) drop out, as do untracked files for the comparisons listed
 * in [COMPARISONS_EXCLUDING_UNTRACKED], as does everything for an unsupported [type].
 *
 * Pure given [entries]: the per-entry revision selection is delegated to [changeForComparison].
 */
fun changesForComparison(
    type: String,
    hash: String?,
    entries: List<SaplingStatusEntry>,
    repoRoot: Path,
    cli: SaplingCli = SaplingCli(),
): List<ComparisonChange> {
    val navigable = if (type in COMPARISONS_EXCLUDING_UNTRACKED) {
        entries.filterNot { it.status == SaplingStatusCode.UNTRACKED }
    } else {
        entries
    }
    return suppressRenameSources(navigable).mapNotNull { entry ->
        changeForComparison(type, hash, entry.status, entry.copySource, repoRoot, entry.path, cli)
            ?.let { ComparisonChange(entry.path, entry.copySource, it) }
    }
}

/**
 * Index of [relativePath] in [changes], or -1 if it is absent (the caller then falls back to a
 * single-file diff). A rename is reported under its new path only, so a click on the old
 * (copy-source) path resolves to the moved file rather than missing.
 */
fun indexOfComparisonChange(changes: List<ComparisonChange>, relativePath: String): Int {
    val exact = changes.indexOfFirst { it.path == relativePath }
    return if (exact >= 0) exact else changes.indexOfFirst { it.copySource == relativePath }
}

/**
 * Lists every file in the comparison via one unscoped `sl status` — the whole-comparison sibling of
 * [classifyForComparison] (see [statusArgsForComparison] for the per-type invocation). Off-EDT:
 * calls [SaplingCli]. Returns an empty list for unsupported comparisons or if `sl` fails, which
 * leaves the caller to fall back to a single-file diff.
 */
fun collectComparisonEntries(
    type: String,
    hash: String?,
    repoRoot: Path,
    cli: SaplingCli = SaplingCli(),
): List<SaplingStatusEntry> {
    val args = statusArgsForComparison(type, hash, null) ?: return emptyList()
    val result = cli.run(repoRoot.toString(), args)
    if (!result.success) return emptyList()
    return parseSaplingStatus(result.stdout)
}

/**
 * Drops any [ComparisonChange] that does not stay inside [repoRoot], applying to every sibling the
 * same containment rule the single clicked file gets: lexical containment for both sides of the
 * change, plus symlink-resolving containment ([resolveWithinRepoReal]) when the after side is the
 * live working copy — a real on-disk read, where a symlink could otherwise escape the repo. Commit
 * diffs and REMOVED/MISSING entries stay lexical-only: the file may legitimately be absent from
 * disk. These paths come from `sl` rather than from the webview, so this is defence in depth.
 */
fun containedComparisonChanges(changes: List<ComparisonChange>, repoRoot: String): List<ComparisonChange> =
    changes.filter { candidate ->
        val lexicalOk = resolveWithinRepoLexical(repoRoot, candidate.path) != null &&
            (candidate.copySource == null || resolveWithinRepoLexical(repoRoot, candidate.copySource) != null)
        val realOk = candidate.change.afterRevision !is CurrentContentRevision ||
            resolveWithinRepoReal(repoRoot, candidate.path) != null
        lexicalOk && realOk
    }
