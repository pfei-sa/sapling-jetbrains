package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.log.SaplingLogEntry

/** Per-commit blame metadata: one entry per distinct node appearing in an annotation. */
data class SaplingBlameCommit(
    val node: String,
    val author: String,
    val dateEpochSeconds: Long,
    val message: String,
)

/**
 * Folds annotate lines together with the (best-effort) batched `sl log` metadata into a deduped,
 * date-descending list of [SaplingBlameCommit]. When a node has no log entry (Call 2 failed or was
 * partial), the author/date fall back to the annotate line and the message is empty — so the tooltip
 * and getRevisions() still work. Blank nodes are skipped.
 */
fun buildBlameCommits(
    lines: List<SaplingBlameLine>,
    logEntries: List<SaplingLogEntry>,
): List<SaplingBlameCommit> {
    val byNode = logEntries.associateBy { it.node }
    val distinct = LinkedHashMap<String, SaplingBlameCommit>()
    for (l in lines) {
        if (l.node.isEmpty() || distinct.containsKey(l.node)) continue
        val log = byNode[l.node]
        distinct[l.node] = SaplingBlameCommit(
            node = l.node,
            author = log?.author ?: l.author,
            dateEpochSeconds = log?.dateEpochSeconds ?: l.dateEpochSeconds,
            message = log?.description ?: "",
        )
    }
    return distinct.values.sortedByDescending { it.dateEpochSeconds }
}
