package io.github.pfeisa.sapling.isl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class IslServerInfo(
    val url: String,
    val port: Int,
    val token: String,
    val pid: Long?,
    val wasReused: Boolean,
)

sealed interface IslLaunchResult {
    data class Ready(val info: IslServerInfo) : IslLaunchResult
    data class Failed(val error: String) : IslLaunchResult
}

@Serializable
private data class RawIslJson(
    val url: String? = null,
    val port: Int? = null,
    val token: String? = null,
    val pid: Long? = null,
    val wasServerReused: Boolean = false,
    val error: String? = null,
)

private val JSON = Json { ignoreUnknownKeys = true }

/** Parses the single JSON object emitted by `sl web --json`. */
fun parseIslLaunch(json: String): IslLaunchResult {
    if (json.isBlank()) return IslLaunchResult.Failed("empty output from sl web")
    val raw = try {
        JSON.decodeFromString<RawIslJson>(json)
    } catch (e: Exception) {
        return IslLaunchResult.Failed("Could not parse sl web output")
    }
    raw.error?.let { return IslLaunchResult.Failed(scrubSlWebOutput(it)) }
    if (raw.url != null && raw.port != null && raw.token != null) {
        return IslLaunchResult.Ready(
            IslServerInfo(raw.url, raw.port, raw.token, raw.pid, raw.wasServerReused)
        )
    }
    return IslLaunchResult.Failed("unexpected sl web output (missing url/port/token)")
}
