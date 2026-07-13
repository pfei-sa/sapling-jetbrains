package io.github.pfeisa.sapling.status

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SaplingStatusCode(val code: Char) {
    MODIFIED('M'),
    ADDED('A'),
    REMOVED('R'),
    CLEAN('C'),
    MISSING('!'),
    UNTRACKED('?'),
    IGNORED('I');

    companion object {
        fun fromCode(s: String): SaplingStatusCode? =
            entries.firstOrNull { it.code.toString() == s }
    }
}

data class SaplingStatusEntry(
    val path: String,
    val status: SaplingStatusCode,
    val copySource: String?,
)

@Serializable
private data class RawStatusEntry(
    val path: String,
    val status: String,
    val copy: String? = null,
)

private val JSON = Json { ignoreUnknownKeys = true }

/** Parses the output of `sl status -Tjson --copies`. Unknown status codes are skipped. */
fun parseSaplingStatus(jsonText: String): List<SaplingStatusEntry> {
    if (jsonText.isBlank()) return emptyList()
    return JSON.decodeFromString<List<RawStatusEntry>>(jsonText).mapNotNull { raw ->
        val code = SaplingStatusCode.fromCode(raw.status) ?: return@mapNotNull null
        SaplingStatusEntry(raw.path, code, raw.copy)
    }
}
