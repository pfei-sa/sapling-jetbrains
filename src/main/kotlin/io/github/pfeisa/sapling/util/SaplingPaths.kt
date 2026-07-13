package io.github.pfeisa.sapling.util

import io.github.pfeisa.sapling.detection.SaplingRepoDetector
import java.nio.file.Path
import java.nio.file.Paths

object SaplingPaths {
    /**
     * Resolves the working-copy root for a path the Sapling VCS has been asked to act on.
     * Matches `.sl` **or** dotgit-mode (`.git`) roots — the same detection ISL uses — so the
     * read providers / command runner also work once a Git-backed Sapling repo is mapped to
     * Sapling. This does NOT widen auto-ownership: `VcsRootChecker` stays `.sl`-only, so
     * Git4Idea keeps auto-claiming `.git` repos; these providers only run for roots already
     * mapped to Sapling (or Sapling actions the user explicitly invoked).
     */
    fun repoRoot(path: Path): Path? = SaplingRepoDetector.findWorkingCopyRoot(path)

    /** Repo-relative, forward-slash path, or null if [file] is outside [root]. */
    fun relative(root: Path, file: Path): String? =
        if (file.startsWith(root)) root.relativize(file).toString().replace('\\', '/') else null
}

/** Lexical containment only (rejects absolute + `../`-escaping paths). Does NOT require existence,
 *  so a committed diff of a file absent from the working copy still validates. Pure/testable —
 *  callers needing symlink resolution + on-disk existence should resolve this further themselves. */
internal fun resolveWithinRepoLexical(repoRoot: String, relativePath: String): Path? {
    val root = Paths.get(repoRoot).normalize()
    val target = root.resolve(relativePath).normalize()
    return if (target.startsWith(root)) target else null
}
