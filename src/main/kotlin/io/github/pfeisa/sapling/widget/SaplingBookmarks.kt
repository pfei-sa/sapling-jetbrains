package io.github.pfeisa.sapling.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry from `sl bookmark -Tjson`. */
data class SaplingBookmark(val name: String, val node: String, val active: Boolean)

@Serializable
private data class RawBookmark(
    val bookmark: String = "",
    val node: String = "",
    val active: Boolean = false,
)

private val JSON = Json { ignoreUnknownKeys = true }

/** Parses `sl bookmark -Tjson`; blank-named entries are dropped. Never throws. */
fun parseBookmarkList(json: String): List<SaplingBookmark> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        JSON.decodeFromString<List<RawBookmark>>(json)
            .filter { it.bookmark.isNotBlank() }
            .map { SaplingBookmark(it.bookmark, it.node, it.active) }
    }.getOrDefault(emptyList())
}
