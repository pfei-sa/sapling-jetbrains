package io.github.pfeisa.sapling.changes

import io.github.pfeisa.sapling.cli.SaplingCli
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber

/** Reads a file's content at a specific revision via `sl cat`. Content is fetched lazily. */
class SaplingContentRevision(
    private val filePath: FilePath,
    private val revision: SaplingRevisionNumber,
    private val repoRoot: String,
    private val relativePath: String,
    private val cli: SaplingCli = SaplingCli(),
) : ByteBackedContentRevision {

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = revision

    /**
     * Raw bytes at the revision. A failed `sl cat` throws [VcsException] (surfacing the
     * error) rather than returning null — null would be read by the IDE as "empty at this
     * revision" and silently corrupt the diff.
     */
    @Throws(VcsException::class)
    override fun getContentAsBytes(): ByteArray {
        // `--` terminates option parsing: a tracked file whose name starts with `-` (e.g.
        // `--config=extensions.x=payload.py`) must never be parsed by `sl` as a flag.
        val result = cli.runForBytes(repoRoot, listOf("cat", "--rev", revision.asString(), "--", relativePath))
        if (!result.success) {
            throw VcsException("sl cat failed for $relativePath@${revision.asString()}: ${result.stderr}")
        }
        return result.stdout
    }

    // Decode with the file's own charset (not a forced UTF-8) so a non-UTF-8 tracked file
    // diffs against the working copy without an encoding-mismatch false diff.
    @Throws(VcsException::class)
    override fun getContent(): String = String(getContentAsBytes(), filePath.charset)
}
