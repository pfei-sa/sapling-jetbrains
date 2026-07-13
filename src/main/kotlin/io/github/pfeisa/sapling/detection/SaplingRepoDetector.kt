package io.github.pfeisa.sapling.detection

import java.nio.file.Files
import java.nio.file.Path

/** Detects Sapling repositories by walking up the directory tree. */
object SaplingRepoDetector {
    const val SAPLING_DIR = ".sl"
    const val GIT_DIR = ".git"

    fun isSaplingRoot(dir: Path): Boolean = Files.isDirectory(dir.resolve(SAPLING_DIR))

    /**
     * A working-copy root `sl` (and therefore ISL) can operate in: either a native `.sl`
     * root or a Git-backed (dotgit-mode) root. Used ONLY to gate ISL availability — VCS root
     * ownership stays `.sl`-only ([isSaplingRoot] / VcsRootChecker), so Git4Idea keeps owning
     * `.git` repos. A plain (non-Sapling) Git repo also matches here; `sl web` then fails
     * gracefully with a user-visible error rather than never offering the tool window.
     */
    fun isWorkingCopyRoot(dir: Path): Boolean =
        isSaplingRoot(dir) || Files.isDirectory(dir.resolve(GIT_DIR))

    fun findRepoRoot(start: Path): Path? = walkUp(start, ::isSaplingRoot)

    /** Like [findRepoRoot] but also matches dotgit-mode roots. See [isWorkingCopyRoot]. */
    fun findWorkingCopyRoot(start: Path): Path? = walkUp(start, ::isWorkingCopyRoot)

    private inline fun walkUp(start: Path, match: (Path) -> Boolean): Path? {
        var current: Path? = start
        while (current != null) {
            if (match(current)) return current
            current = current.parent
        }
        return null
    }
}
