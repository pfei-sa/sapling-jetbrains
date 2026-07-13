package io.github.pfeisa.sapling.blame

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SaplingBlameLine(
    val lineNumber: Int,
    val node: String,
    val author: String,
    val dateEpochSeconds: Long,
    val content: String,
)

@Serializable
private data class RawAnnotateLine(
    val line: String = "",
    val node: String = "",
    val user: String = "",
    val date: List<Double> = emptyList(),
)

@Serializable
private data class RawAnnotateFile(
    val path: String = "",
    val lines: List<RawAnnotateLine> = emptyList(),
)

private val JSON = Json { ignoreUnknownKeys = true }

/**
 * Parses `sl annotate -Tjson` (array of files, each with a `lines` array).
 * sl emits the epoch as a float and no explicit line-number field, so the
 * 1-based line number is derived from position.
 */
fun parseSaplingAnnotate(json: String): List<SaplingBlameLine> {
    if (json.isBlank()) return emptyList()
    val files = JSON.decodeFromString<List<RawAnnotateFile>>(json)
    return files.flatMap { file ->
        file.lines.mapIndexed { index, raw ->
            SaplingBlameLine(
                lineNumber = index + 1,
                node = raw.node,
                author = raw.user,
                dateEpochSeconds = raw.date.firstOrNull()?.toLong() ?: 0L,
                content = raw.line,
            )
        }
    }
}
