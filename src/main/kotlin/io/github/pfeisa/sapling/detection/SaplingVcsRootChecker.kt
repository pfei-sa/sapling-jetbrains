package io.github.pfeisa.sapling.detection

import io.github.pfeisa.sapling.SaplingVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile

/**
 * Auto-detects only `.sl`-mode roots. dotgit-mode roots are intentionally left to
 * Git4Idea by default; the Sapling VCS stays available for manual mapping.
 *
 * Overrides the non-deprecated `isRoot(VirtualFile)` (not the deprecated
 * `isRoot(String)`) for forward compatibility with the open-ended untilBuild.
 */
class SaplingVcsRootChecker : VcsRootChecker() {
    override fun getSupportedVcs(): VcsKey = SaplingVcs.KEY

    override fun isVcsDir(dirName: String): Boolean = dirName == SaplingRepoDetector.SAPLING_DIR

    override fun isRoot(file: VirtualFile): Boolean {
        // toNioPath() throws for non-local (e.g. in-memory) VirtualFiles; such a file is
        // simply not a Sapling root, so report false rather than letting it propagate.
        val path = runCatching { file.toNioPath() }.getOrNull() ?: return false
        return SaplingRepoDetector.isSaplingRoot(path)
    }
}
