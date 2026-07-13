package io.github.pfeisa.sapling.history

import io.github.pfeisa.sapling.changes.SaplingContentRevision
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.cli.SaplingCli
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Paths
import java.util.Date

class SaplingFileRevision(
    private val node: String,
    private val author: String,
    private val dateEpochSeconds: Long,
    private val message: String,
    private val repoRoot: String,
    private val relativePath: String,
    private val cli: SaplingCli,
) : VcsFileRevision {

    private val number = SaplingRevisionNumber(node)

    /** Content-at-revision goes through the shared [SaplingContentRevision] (single `sl cat` path). */
    private val contentRevision = SaplingContentRevision(
        VcsUtil.getFilePath(Paths.get(repoRoot).resolve(relativePath).toFile(), false),
        number,
        repoRoot,
        relativePath,
        cli,
    )

    override fun getRevisionNumber(): VcsRevisionNumber = number
    override fun getRevisionDate(): Date = Date(dateEpochSeconds * 1000)
    override fun getAuthor(): String = author
    override fun getCommitMessage(): String = message
    override fun getBranchName(): String? = null
    override fun getChangedRepositoryPath(): com.intellij.openapi.vcs.RepositoryLocation? = null

    @Volatile
    private var cached: ByteArray? = null

    override fun loadContent(): ByteArray? =
        contentRevision.contentAsBytes.also { cached = it }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getContent(): ByteArray? = cached ?: loadContent()
}
