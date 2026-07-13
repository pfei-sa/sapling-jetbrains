package io.github.pfeisa.sapling.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SaplingLogEntry(
    val node: String,
    val author: String,
    val dateEpochSeconds: Long,
    val description: String,
    val parents: List<String>,
    val bookmarks: List<String>,
)

@Serializable
private data class RawLogEntry(
    val node: String,
    val user: String = "",
    val date: List<Long> = emptyList(),
    val desc: String = "",
    val parents: List<String> = emptyList(),
    val bookmarks: List<String> = emptyList(),
)

private val JSON = Json { ignoreUnknownKeys = true }

/** Parses `sl log -Tjson`. `date` is `[epochSeconds, tzOffset]`. */
fun parseSaplingLog(json: String): List<SaplingLogEntry> {
    if (json.isBlank()) return emptyList()
    return JSON.decodeFromString<List<RawLogEntry>>(json).map { raw ->
        SaplingLogEntry(
            node = raw.node,
            author = raw.user,
            dateEpochSeconds = raw.date.firstOrNull() ?: 0L,
            description = raw.desc,
            parents = raw.parents,
            bookmarks = raw.bookmarks,
        )
    }
}
