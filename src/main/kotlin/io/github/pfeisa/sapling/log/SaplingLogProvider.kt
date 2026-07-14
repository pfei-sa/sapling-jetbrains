package io.github.pfeisa.sapling.log

import io.github.pfeisa.sapling.SaplingVcs
import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.changes.commitStatusEntryToChange
import io.github.pfeisa.sapling.changes.suppressRenameSources
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.status.parseSaplingStatus
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.openapi.vcs.VcsKey
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.LogDataImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
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

    /** Builds a [VcsCommitMetadata] from a parsed [SaplingLogEntry]. Shared by the read* methods. */
    private fun buildMetadata(
        entry: SaplingLogEntry,
        root: VirtualFile,
        f: VcsLogObjectsFactory,
    ): VcsCommitMetadata {
        val (authorName, authorEmail) = parseAuthor(entry.author)
        val ts = entry.dateEpochSeconds * 1000L
        return f.createCommitMetadata(
            f.createHash(entry.node),
            entry.parents.map { f.createHash(it) },
            ts,
            root,
            entry.description.lineSequence().firstOrNull() ?: "",
            authorName,
            authorEmail,
            entry.description,
            authorName,   // Sapling has no separate committer
            authorEmail,
            ts,
        )
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
        return parseSaplingLog(result.stdout).map { entry -> buildMetadata(entry, root, f) }
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
     * Loads *all* commits for the full graph, **streaming** each to [consumer] as `sl` emits it (peak
     * memory is O(one line), not O(history)). Uses a NUL-delimited line template so filenames/authors
     * with spaces are unambiguous and the whole blob is never buffered or JSON-parsed at once.
     */
    @Throws(VcsException::class)
    override fun readAllHashes(
        root: VirtualFile,
        consumer: Consumer<in TimedVcsCommit>,
    ): VcsLogProvider.LogData {
        val f = factory()
        val users = mutableSetOf<VcsUser>()
        // Fields: node, p1node, p2node, hgdate ("<epoch> <tz>"), author — NUL-separated, newline-terminated.
        val template = "{node}\\0{p1node}\\0{p2node}\\0{date|hgdate}\\0{author}\\n"
        val result = cli.runStreaming(root.path, listOf("log", "-T", template)) { line ->
            val commit = parseStreamedCommitLine(line) ?: return@runStreaming
            consumer.consume(
                f.createTimedCommit(
                    f.createHash(commit.node),
                    commit.parents.map { f.createHash(it) },
                    commit.timestampMs,
                )
            )
            val (name, email) = parseAuthor(commit.author)
            users.add(f.createUser(name, email))
        }
        if (!result.success) {
            throw VcsException("sl log (streaming) failed with exit code ${result.exitCode}: ${result.stderr}")
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
            consumer.consume(buildMetadata(entry, root, f))
        }
    }

    /**
     * Provides full commit details (metadata + file changes) for the Log's "Changes" panel.
     *
     * Runs one `sl status --change <node> -Tjson --copies` per commit, **off the EDT**, sequentially
     * (honouring cancellation between commits), and caches the resulting [Change] list in a
     * [SaplingFullCommitDetails] whose `getChanges()` merely returns it — because the platform calls
     * `getChanges()` on the EDT. For a **merge** commit the change set vs each further parent is also
     * precomputed (`sl status --rev <parent> --rev <node>`), so `getChanges(parentIndex)` is EDT-safe too.
     */
    @Throws(VcsException::class)
    override fun readFullDetails(
        root: VirtualFile,
        hashes: List<String>,
        consumer: Consumer<in VcsFullCommitDetails>,
    ) {
        if (hashes.isEmpty()) return
        val revset = hashes.joinToString("+")
        val result = cli.run(root.path, listOf("log", "-Tjson", "-r", revset))
        // No ProgressIndicator is passed to cli.run here or in computeCommitChanges below, so
        // `.cancelled` can never actually flip true on this path — it's a defensive no-op.
        // Real cancellation for this multi-subprocess loop comes from the
        // ProgressManager.checkCanceled() calls between commits.
        if (result.cancelled) throw ProcessCanceledException()
        if (!result.success) throw VcsException("sl log failed: ${result.stderr}")
        val f = factory()
        val rootPath = root.toNioPath()
        parseSaplingLog(result.stdout).forEach { entry ->
            ProgressManager.checkCanceled()
            val meta = buildMetadata(entry, root, f)
            val changesByParent = HashMap<Int, List<Change>>()
            val firstParent = entry.parents.firstOrNull()?.let { SaplingRevisionNumber(it) }
            // Key 0: vs first parent (sl computes this automatically for --change).
            changesByParent[0] = computeCommitChanges(root.path, rootPath, entry.node, firstParent, comparedParent = null)
            // Merge: also compute vs each further parent so getChanges(i) is cached.
            if (entry.parents.size > 1) {
                entry.parents.forEachIndexed { i, parent ->
                    if (i == 0) return@forEachIndexed
                    ProgressManager.checkCanceled()
                    changesByParent[i] =
                        computeCommitChanges(root.path, rootPath, entry.node, SaplingRevisionNumber(parent), comparedParent = parent)
                }
            }
            consumer.consume(SaplingFullCommitDetails(meta, changesByParent))
        }
    }

    /**
     * Runs one `sl status` for a commit and maps it to [Change]s (both sides content-at-revision).
     * [comparedParent] null → `--change <node>` (vs first parent); non-null → `--rev <parent> --rev <node>`.
     * [beforeRev] is the parent the change is computed against (null only for the root commit).
     */
    private fun computeCommitChanges(
        rootStr: String,
        rootPath: Path,
        node: String,
        beforeRev: SaplingRevisionNumber?,
        comparedParent: String?,
    ): List<Change> {
        val args = if (comparedParent == null) {
            listOf("status", "--change", node, "-Tjson", "--copies")
        } else {
            listOf("status", "--rev", comparedParent, "--rev", node, "-Tjson", "--copies")
        }
        val res = cli.run(rootStr, args)
        // Defensive no-op here too — see the comment on the equivalent guard in readFullDetails.
        if (res.cancelled) throw ProcessCanceledException()
        if (!res.success) throw VcsException("sl status for $node failed: ${res.stderr}")
        val afterRev = SaplingRevisionNumber(node)
        return suppressRenameSources(parseSaplingStatus(res.stdout))
            .mapNotNull { commitStatusEntryToChange(it, rootPath, beforeRev, afterRev, cli) }
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
    // VcsLogProvider — toolbar filters
    // ------------------------------------------------------------------

    /**
     * Returns commits matching [filters], bounded by [maxCount], by translating the filter collection
     * into flag-form `sl log` args (see [buildLogFilterArgs]) and parsing the result. Overriding this
     * 4-arg overload (present since 2024.2) is what makes the Log toolbar's text/user/date/path/hash
     * filters work while `SUPPORTS_INDEXING` stays false. The [options] (graph layout) do not affect
     * which commits match, so they are ignored here.
     */
    @Throws(VcsException::class)
    override fun getCommitsMatchingFilter(
        root: VirtualFile,
        filters: VcsLogFilterCollection,
        options: PermanentGraph.Options,
        maxCount: Int,
    ): List<TimedVcsCommit> {
        val date = filters.get(VcsLogFilterCollection.DATE_FILTER)
        val text = filters.get(VcsLogFilterCollection.TEXT_FILTER)
        val paths = filters.get(VcsLogFilterCollection.STRUCTURE_FILTER)
            ?.files?.mapNotNull { relativizeForRoot(root, it) } ?: emptyList()

        val filterArgs = buildLogFilterArgs(
            users = filters.get(VcsLogFilterCollection.USER_FILTER)?.valuesAsText?.toList() ?: emptyList(),
            afterSpec = date?.after?.let(::formatSlDate),
            beforeSpec = date?.before?.let(::formatSlDate),
            text = text?.text,
            paths = paths,
            hashes = filters.get(VcsLogFilterCollection.HASH_FILTER)?.hashes?.toList() ?: emptyList(),
        )
        // Filter kinds we do not translate are left unapplied (never a wrong subset) — log if present.
        val unsupported = filters.filters.map { it.key }.filter {
            it != VcsLogFilterCollection.USER_FILTER && it != VcsLogFilterCollection.DATE_FILTER &&
                it != VcsLogFilterCollection.TEXT_FILTER && it != VcsLogFilterCollection.STRUCTURE_FILTER &&
                it != VcsLogFilterCollection.HASH_FILTER
        }
        if (unsupported.isNotEmpty()) LOG.info("Sapling log: unsupported filter(s) ignored: $unsupported")

        val args = mutableListOf("log", "-Tjson")
        if (maxCount > 0) { args += "-l"; args += maxCount.toString() }
        args += filterArgs

        val result = cli.run(root.path, args)
        if (!result.success) throw VcsException("sl log (filtered) failed: ${result.stderr}")
        val f = factory()
        return parseSaplingLog(result.stdout).map { entry ->
            f.createTimedCommit(
                f.createHash(entry.node),
                entry.parents.map { f.createHash(it) },
                entry.dateEpochSeconds * 1000L,
            )
        }
    }

    /**
     * Legacy 3-arg overload — delegates to the 4-arg one so the fix applies regardless of which
     * overload the platform invokes (both exist since 2024.2). The platform member itself is
     * `@Deprecated` (superseded by the 4-arg overload); narrowly suppressed rather than
     * propagating `@Deprecated` onto this override, since it is still live dispatch surface.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    @Throws(VcsException::class)
    override fun getCommitsMatchingFilter(
        root: VirtualFile,
        filters: VcsLogFilterCollection,
        maxCount: Int,
    ): List<TimedVcsCommit> =
        getCommitsMatchingFilter(root, filters, PermanentGraph.Options.Default, maxCount)

    /** Formats a filter [Date] as an `sl`-accepted date string (`yyyy-MM-dd HH:mm:ss`, all-numeric/locale-safe). */
    private fun formatSlDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)

    /** Repo-relative path for a structure-filter [file], or null if it is outside [root]. */
    private fun relativizeForRoot(root: VirtualFile, file: FilePath): String? =
        SaplingPaths.relative(root.toNioPath(), file.ioFile.toPath())

    // ------------------------------------------------------------------
    // VcsLogProvider — feature properties
    // ------------------------------------------------------------------

    /**
     * Returns the value for each [VcsLogProperties] capability flag.
     *
     * - LIGHTWEIGHT_BRANCHES: true  — bookmarks are cheap (no copy-on-write like SVN branches).
     * - SUPPORTS_INDEXING: false    — we do not implement the VcsLogIndex SPI.
     * - HAS_COMMITTER: false        — Sapling stores only author, not a separate committer.
     * - SUPPORTS_PARENTS_FILTER: false — [getCommitsMatchingFilter] does not translate a PARENT
     *   filter into `sl log` args (it falls into the logged "unsupported, ignored" bucket), so
     *   advertising `true` would make the platform trust us to have already applied it and skip
     *   its own filtering — silently returning a superset. `false` makes the platform apply the
     *   PARENT filter itself against the graph from [readAllHashes], which is strictly safe.
     * - SUPPORTS_INCREMENTAL_REFRESH: false — we do not implement incremental refresh; this
     *   property's platform default is `true`, so we must actively decline it. The field was
     *   removed in build 261, so it is resolved reflectively (see [incrementalRefreshProp]) rather
     *   than referenced directly, to stay binary-compatible across 242 → 262+.
     * - Any property we do not answer returns null → the platform uses that property's own default.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? {
        if (incrementalRefreshProp != null && property === incrementalRefreshProp) return false as T
        return when (property) {
            VcsLogProperties.LIGHTWEIGHT_BRANCHES -> true as T
            VcsLogProperties.SUPPORTS_INDEXING -> false as T
            VcsLogProperties.HAS_COMMITTER -> false as T
            VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY -> false as T
            VcsLogProperties.CASE_INSENSITIVE_REGEX -> false as T
            VcsLogProperties.SUPPORTS_PARENTS_FILTER -> false as T
            else -> null
        }
    }

    /**
     * `VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH`, resolved reflectively because the field was
     * removed in IntelliJ 261 — a direct `getstatic` would be an unresolved-field binary
     * incompatibility there. Null when the field is absent (261+), in which case the platform no
     * longer has the concept and there is nothing to decline.
     */
    private val incrementalRefreshProp: VcsLogProperties.VcsLogProperty<*>? = runCatching {
        VcsLogProperties::class.java.getField("SUPPORTS_INCREMENTAL_REFRESH").get(null)
            as VcsLogProperties.VcsLogProperty<*>
    }.getOrNull()
}
