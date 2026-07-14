package io.github.pfeisa.sapling.log

/** A commit parsed from the NUL-delimited streaming `sl log` template (see SaplingLogProvider.readAllHashes). */
data class StreamedCommit(
    val node: String,
    val parents: List<String>,
    val timestampMs: Long,
    val author: String,
)

/** Sapling's "null node" — the placeholder `sl` prints for an absent parent in `{p1node}`/`{p2node}`. */
private const val NULL_NODE = "0000000000000000000000000000000000000000"

/**
 * Parses one line of `sl log -T '{node}\0{p1node}\0{p2node}\0{date|hgdate}\0{author}\n'`.
 * Fields are NUL-separated. `{date|hgdate}` is `"<epochSeconds> <tzOffset>"` — the epoch seconds
 * (first token) become milliseconds. Absent parents render as [NULL_NODE] and are dropped.
 * Returns null for a blank or malformed line (fewer than the expected fields).
 */
fun parseStreamedCommitLine(line: String): StreamedCommit? {
    if (line.isBlank()) return null
    val f = line.split('\u0000')
    if (f.size < 4) return null
    val epochSeconds = f[3].trim().substringBefore(' ').toLongOrNull() ?: return null
    val parents = listOf(f[1], f[2]).filter { it.isNotEmpty() && it != NULL_NODE }
    return StreamedCommit(
        node = f[0],
        parents = parents,
        timestampMs = epochSeconds * 1000L,
        author = if (f.size >= 5) f[4] else "",
    )
}
