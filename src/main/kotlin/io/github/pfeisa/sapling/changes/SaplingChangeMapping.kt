package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.SaplingStatusCode
import io.github.pfeisa.sapling.status.SaplingStatusEntry
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path

/**
 * `sl mv old new` reports the rename as TWO status entries: `A new (copy=old)` **and** `R old`.
 * Mapped independently, `old` would surface twice — once as the move's before-side and once as a
 * standalone deletion (and two Changes would share the before-path `old`, tripping platform
 * "duplicate revision" warnings). Drop each `R` entry whose path is the copy-source of an `A`
 * entry so a rename shows up as a single moved [Change]. A plain `sl copy` (no `R`) is unaffected.
 */
fun suppressRenameSources(entries: List<SaplingStatusEntry>): List<SaplingStatusEntry> {
    val copySources = entries
        .filter { it.status == SaplingStatusCode.ADDED }
        .mapNotNull { it.copySource }
        .toSet()
    if (copySources.isEmpty()) return entries
    return entries.filterNot { it.status == SaplingStatusCode.REMOVED && it.path in copySources }
}

/**
 * Maps one `sl status` entry to an IDE [Change]. Only M/A/R are turned into changes
 * here; untracked/ignored/missing handling arrives in a later plan.
 */
fun statusEntryToChange(
    entry: SaplingStatusEntry,
    repoRoot: Path,
    parentRev: SaplingRevisionNumber,
    cli: SaplingCli,
): Change? {
    val rootStr = repoRoot.toString()

    fun filePathOf(relative: String) =
        VcsUtil.getFilePath(repoRoot.resolve(relative).toFile(), false)

    fun before(relative: String) =
        SaplingContentRevision(filePathOf(relative), parentRev, rootStr, relative, cli)

    return when (entry.status) {
        SaplingStatusCode.MODIFIED ->
            Change(before(entry.path), CurrentContentRevision(filePathOf(entry.path)))

        SaplingStatusCode.ADDED -> {
            val source = entry.copySource
            if (source != null) {
                Change(before(source), CurrentContentRevision(filePathOf(entry.path)))
            } else {
                Change(null, CurrentContentRevision(filePathOf(entry.path)))
            }
        }

        SaplingStatusCode.REMOVED ->
            Change(before(entry.path), null)

        else -> null
    }
}
