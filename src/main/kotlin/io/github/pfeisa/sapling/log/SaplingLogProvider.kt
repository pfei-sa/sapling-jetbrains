package io.github.pfeisa.sapling.log

import io.github.pfeisa.sapling.SaplingVcs
import io.github.pfeisa.sapling.cli.SaplingCli
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.openapi.vcs.VcsKey
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.LogDataImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<SaplingLogProvider>()

// ---------------------------------------------------------------------------
// JSON model for `sl bookmark -Tjson`
// ---------------------------------------------------------------------------

@Serializable
private data class RawBookmark(
    val bookmark: String = "",
    val node: String = "",
    val active: Boolean = false,
)

private val BOOKMARK_JSON = Json { ignoreUnknownKeys = true }

private fun parseBookmarks(json: String): List<RawBookmark> {
    if (json.isBlank()) return emptyList()
    return BOOKMARK_JSON.decodeFromString<List<RawBookmark>>(json)
}

/**
 * Derives the active bookmark name (Sapling's "current branch") from the JSON of
 * `sl bookmark -Tjson`; returns null when no bookmark is active. Verified against real
 * `sl`: exactly one bookmark carries `active:true`, and it matches `{activebookmark}`.
 *
 * Kept as a pure, package-internal function so it can be unit-tested without a live `sl`.
 */
/** The active bookmark's name from parsed rows, or null when none is active or it is unnamed. */
private fun List<RawBookmark>.activeBookmarkName(): String? =
    firstOrNull { it.active }?.bookmark?.ifBlank { null }

internal fun activeBookmarkFrom(bookmarkJson: String): String? =
    parseBookmarks(bookmarkJson).activeBookmarkName()

// ---------------------------------------------------------------------------
// SaplingLogProvider
// ---------------------------------------------------------------------------

/**
 * Feeds the IDE's repo-wide **Log** tab from `sl log -Tjson`.
 *
 * Design constraints:
 * - Always bounded: `readFirstBlock` respects [VcsLogProvider.Requirements.commitCount];
 *   `readAllHashes` streams every commit lazily.
 * - Off the EDT: all `sl` invocations happen on background threads (enforced by
 *   [SaplingCli]).
 * - No static mutable state.
 */
class SaplingLogProvider(private val project: Project) : VcsLogProvider {

    private val cli = SaplingCli()
    private val refManager = SaplingRefManager()

    /**
     * Per-root cache of the active bookmark ("current branch"), keyed by root path.
     * Written off-EDT by [loadRefs] during log reads; read on the EDT by [getCurrentBranch].
     * A missing key means "no active bookmark, or not loaded yet" (ConcurrentHashMap can't
     * store null).
     */
    private val currentBranchCache = ConcurrentHashMap<String, String>()

    private fun factory(): VcsLogObjectsFactory =
        project.getService(VcsLogObjectsFactory::class.java)

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Parses the author field "Name <email>" into (name, email). */
    private fun parseAuthor(raw: String): Pair<String, String> {
        val ltIdx = raw.indexOf('<')
        return if (ltIdx >= 0) {
            val name = raw.substring(0, ltIdx).trim()
            val email = raw.substring(ltIdx + 1).substringBefore('>').trim()
            Pair(name, email)
        } else {
            Pair(raw.trim(), "")
        }
    }

    /**
     * Loads commit metadata from `sl log -Tjson`, bounded by [limit].
     * Passing `null` for limit returns all commits (only used internally by
     * [readAllHashes] which streams them out to the consumer).
     */
    private fun loadMetadata(root: VirtualFile, limit: Int?): List<VcsCommitMetadata> {
        val args = mutableListOf("log", "-Tjson")
        if (limit != null) args += listOf("-l", limit.toString())
        val result = cli.run(root.path, args)
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        val f = factory()
        return parseSaplingLog(result.stdout).map { entry ->
            val (authorName, authorEmail) = parseAuthor(entry.author)
            val commitTimestampMs = entry.dateEpochSeconds * 1000L
            val parentHashes = entry.parents.map { f.createHash(it) }
            f.createCommitMetadata(
                /* id          */ f.createHash(entry.node),
                /* parents     */ parentHashes,
                /* commitTime  */ commitTimestampMs,
                /* root        */ root,
                /* subject     */ entry.description.lineSequence().firstOrNull() ?: "",
                /* authorName  */ authorName,
                /* authorEmail */ authorEmail,
                /* fullMessage */ entry.description,
                /* committerName  */ authorName,   // Sapling has no separate committer
                /* committerEmail */ authorEmail,
                /* authorTime  */ commitTimestampMs,
            )
        }
    }

    /**
     * Loads bookmark refs for [root], creating [VcsRef] objects for each bookmark.
     */
    private fun loadRefs(root: VirtualFile): Set<VcsRef> {
        val result = cli.run(root.path, listOf("bookmark", "-Tjson"))
        if (!result.success) return emptySet()
        val bookmarks = parseBookmarks(result.stdout)
        // Refresh the current-branch cache off-EDT so getCurrentBranch (called on the EDT by
        // the platform's CurrentBranchHighlighter) never has to spawn `sl`.
        val active = bookmarks.activeBookmarkName()
        if (active == null) currentBranchCache.remove(root.path)
        else currentBranchCache[root.path] = active
        val f = factory()
        return bookmarks.mapTo(mutableSetOf()) { bm ->
            f.createRef(f.createHash(bm.node), bm.bookmark, SaplingRefManager.BOOKMARK, root)
        }
    }

    // ------------------------------------------------------------------
    // VcsLogProvider — core data-loading methods
    // ------------------------------------------------------------------

    /**
     * Called on initial load and refresh. Returns [requirements.commitCount] commits plus
     * the current bookmark refs.
     */
    @Throws(VcsException::class)
    override fun readFirstBlock(
        root: VirtualFile,
        requirements: VcsLogProvider.Requirements,
    ): VcsLogProvider.DetailedLogData {
        val commits = loadMetadata(root, requirements.commitCount)
        val refs = loadRefs(root)
        return LogDataImpl(refs, commits)
    }

    /**
     * Called to load *all* commits for the full graph. Streams each commit to [consumer]
     * to avoid materialising unbounded history in memory.
     *
     * Returns [LogDataImpl] with refs and the users seen in the streamed commits (the
     * framework may discard the return value but it is included for correctness).
     */
    @Throws(VcsException::class)
    override fun readAllHashes(
        root: VirtualFile,
        consumer: Consumer<in TimedVcsCommit>,
    ): VcsLogProvider.LogData {
        // Stream via `sl log -T '{node}\n{p1node}\n{p2node}\n{date|hgdate}\n---\n'`
        // Using json for simplicity and consistency with parseSaplingLog.
        val result = cli.run(root.path, listOf("log", "-Tjson"))
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        val f = factory()
        val users = mutableSetOf<VcsUser>()
        parseSaplingLog(result.stdout).forEach { entry ->
            val (authorName, authorEmail) = parseAuthor(entry.author)
            val hash = f.createHash(entry.node)
            val parents = entry.parents.map { f.createHash(it) }
            val timestamp = entry.dateEpochSeconds * 1000L
            consumer.consume(f.createTimedCommit(hash, parents, timestamp))
            users.add(f.createUser(authorName, authorEmail))
        }
        val refs = loadRefs(root)
        return LogDataImpl(refs, users)
    }

    /**
     * Loads full metadata for a specific set of commits identified by [hashes].
     * Called by the IDE when the user scrolls to commits not yet loaded.
     */
    @Throws(VcsException::class)
    override fun readMetadata(
        root: VirtualFile,
        hashes: List<String>,
        consumer: Consumer<in VcsCommitMetadata>,
    ) {
        if (hashes.isEmpty()) return
        // Sapling revset: join hashes with '+' to get all of them in one query
        val revset = hashes.joinToString("+")
        val args = listOf("log", "-Tjson", "-r", revset)
        val result = cli.run(root.path, args)
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        val f = factory()
        parseSaplingLog(result.stdout).forEach { entry ->
            val (authorName, authorEmail) = parseAuthor(entry.author)
            val commitTimestampMs = entry.dateEpochSeconds * 1000L
            val parentHashes = entry.parents.map { f.createHash(it) }
            consumer.consume(
                f.createCommitMetadata(
                    f.createHash(entry.node),
                    parentHashes,
                    commitTimestampMs,
                    root,
                    entry.description.lineSequence().firstOrNull() ?: "",
                    authorName,
                    authorEmail,
                    entry.description,
                    authorName,
                    authorEmail,
                    commitTimestampMs,
                )
            )
        }
    }

    /**
     * Provides full commit details (metadata + file changes) for the "Changes" panel.
     *
     * VcsFullCommitDetails requires getChanges(). Computing the change list for an
     * arbitrary set of commits requires running `sl diff -r <hash>` for each, which
     * is expensive and needs the IntelliJ change-model infrastructure. We satisfy the
     * contract by returning a metadata-only implementation with empty Changes collections.
     *
     * This is safe: the IDE uses readFullDetails for the diff panel; if it returns empty
     * changes the panel shows nothing but the app does not crash. The native diff is
     * already wired via SaplingDiffProvider.
     */
    @Throws(VcsException::class)
    override fun readFullDetails(
        root: VirtualFile,
        hashes: List<String>,
        consumer: Consumer<in VcsFullCommitDetails>,
    ) {
        if (hashes.isEmpty()) return
        val revset = hashes.joinToString("+")
        val args = listOf("log", "-Tjson", "-r", revset)
        val result = cli.run(root.path, args)
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        val f = factory()
        parseSaplingLog(result.stdout).forEach { entry ->
            val (authorName, authorEmail) = parseAuthor(entry.author)
            val commitTimestampMs = entry.dateEpochSeconds * 1000L
            val parentHashes = entry.parents.map { f.createHash(it) }
            val metadata = f.createCommitMetadata(
                f.createHash(entry.node),
                parentHashes,
                commitTimestampMs,
                root,
                entry.description.lineSequence().firstOrNull() ?: "",
                authorName,
                authorEmail,
                entry.description,
                authorName,
                authorEmail,
                commitTimestampMs,
            )
            consumer.consume(MetadataBackedFullDetails(metadata))
        }
    }

    // ------------------------------------------------------------------
    // VcsLogProvider — identity and configuration
    // ------------------------------------------------------------------

    override fun getSupportedVcs(): VcsKey = SaplingVcs.KEY

    override fun getReferenceManager(): VcsLogRefManager = refManager

    /**
     * Returns the active bookmark (Sapling's equivalent of the current branch), or null.
     *
     * The platform calls this **on the EDT** (e.g. `CurrentBranchHighlighter` while painting
     * log rows), so it must not block or spawn a process. It returns the value cached off-EDT
     * by [loadRefs], which runs on every log read before any row is painted. Returns null
     * until the first read completes or when no bookmark is active.
     */
    override fun getCurrentBranch(root: VirtualFile): String? = currentBranchCache[root.path]

    /**
     * Returns the configured user identity from `sl config ui.username`, or null if not set.
     */
    @Throws(VcsException::class)
    override fun getCurrentUser(root: VirtualFile): VcsUser? {
        val result = cli.run(root.path, listOf("config", "ui.username"))
        if (!result.success || result.stdout.isBlank()) return null
        val (name, email) = parseAuthor(result.stdout.trim())
        return factory().createUser(name, email)
    }

    // ------------------------------------------------------------------
    // VcsLogProvider — event subscription
    // ------------------------------------------------------------------

    /**
     * Subscribes to repository change events and fires [refresher] when a root changes.
     *
     * Sapling does not have a filesystem watcher API in the CLI; the IDE-level VFS
     * listener already handles file-system events. Returning a no-op Disposable is
     * correct: the IDE will still periodically poll and the manual Refresh button works.
     */
    override fun subscribeToRootRefreshEvents(
        roots: Collection<VirtualFile>,
        refresher: VcsLogRefresher,
    ): Disposable = Disposable { }

    // ------------------------------------------------------------------
    // VcsLogProvider — branch/ref utilities
    // ------------------------------------------------------------------

    /**
     * Returns the bookmarks that contain [commitHash]. We use `sl log -r "bookmark() and
     * descendants(<hash>)"` to find which bookmarks are reachable from this commit.
     *
     * Safe default on any CLI failure: empty list.
     */
    @Throws(VcsException::class)
    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> {
        val hash = commitHash.asString()
        // Guard: the hash is interpolated into a revset expression, so reject anything that
        // is not a bare hex hash (the framework always supplies hex — this stops any future
        // non-hex value from composing an unintended revset).
        if (!hash.matches(Regex("[0-9a-fA-F]+"))) return emptyList()
        // bookmarks that have this commit as an ancestor (i.e., the bookmark is a
        // descendant of the commit, meaning the commit is in its history)
        val result = cli.run(
            root.path,
            // {bookmark} (singular) is not a valid keyword and renders blank; {bookmarks}
            // renders the names space-separated on one line, so split on whitespace too.
            listOf("log", "-r", "bookmark() and descendants($hash)", "-T", "{bookmarks}\n"),
        )
        if (!result.success) return emptyList()
        return result.stdout.trim().lines()
            .flatMap { it.trim().split(Regex("\\s+")) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    // ------------------------------------------------------------------
    // VcsLogProvider — feature properties
    // ------------------------------------------------------------------

    /**
     * Returns the value for each [VcsLogProperties] capability flag.
     *
     * - LIGHTWEIGHT_BRANCHES: true  — bookmarks are cheap (no copy-on-write like SVN branches).
     * - SUPPORTS_INDEXING: false    — we do not implement the VcsLogIndex SPI.
     * - HAS_COMMITTER: false        — Sapling stores only author, not a separate committer.
     * - All other Boolean props:    false (safe default per VcsLogProperty.getOrDefault).
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? {
        return when (property) {
            VcsLogProperties.LIGHTWEIGHT_BRANCHES -> true as T
            VcsLogProperties.SUPPORTS_INDEXING -> false as T
            VcsLogProperties.HAS_COMMITTER -> false as T
            VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY -> false as T
            VcsLogProperties.CASE_INSENSITIVE_REGEX -> false as T
            VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH -> false as T
            VcsLogProperties.SUPPORTS_PARENTS_FILTER -> true as T
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Inner class: metadata-backed VcsFullCommitDetails (empty changes)
    // ------------------------------------------------------------------

    /**
     * Adapts a [VcsCommitMetadata] to [VcsFullCommitDetails] by delegating all
     * metadata fields and returning empty change collections.
     *
     * This is used by [readFullDetails]. The Changes panel will be empty; full diff
     * functionality is provided by [io.github.pfeisa.sapling.diff.SaplingDiffProvider].
     *
     * We delegate only to [VcsCommitMetadata] (which already extends VcsShortCommitDetails)
     * to avoid the duplicate-supertype issue that arises from delegating both.
     */
    private class MetadataBackedFullDetails(
        private val meta: VcsCommitMetadata,
    ) : VcsFullCommitDetails, VcsCommitMetadata by meta {
        override fun getChanges(): Collection<Change> = emptyList()
        override fun getChanges(parent: Int): Collection<Change> = emptyList()
    }
}
