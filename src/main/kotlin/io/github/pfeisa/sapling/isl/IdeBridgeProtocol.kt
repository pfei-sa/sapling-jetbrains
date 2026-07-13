package io.github.pfeisa.sapling.isl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The `comparison` payload ISL passes to `platform.openDiff`; see design spec §2. */
@Serializable
internal data class Comparison(
    val type: String,
    val hash: String? = null,
)

/** An untrusted `window.__IdeBridge` payload. */
@Serializable
internal data class BridgeMessage(
    val t: String,
    val path: String? = null,
    val line: Int? = null,
    val data: String? = null,
    val comparison: Comparison? = null,
)

private val BRIDGE_JSON = Json { ignoreUnknownKeys = true }

/** Parses an untrusted bridge payload; returns null on malformed input (never throws). */
internal fun parseBridgeMessage(raw: String): BridgeMessage? =
    try {
        BRIDGE_JSON.decodeFromString<BridgeMessage>(raw)
    } catch (e: Exception) {
        null
    }
