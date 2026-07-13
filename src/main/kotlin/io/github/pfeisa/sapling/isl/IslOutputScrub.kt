package io.github.pfeisa.sapling.isl

/**
 * Strips URLs and token query params so `sl web` diagnostics can be logged or surfaced to the
 * user without leaking the auth token. Shared by [IslServerManager] (stderr logging) and
 * [parseIslLaunch] (the JSON `error` field shown in a notification).
 */
internal fun scrubSlWebOutput(text: String): String =
    text.replace(Regex("https?://\\S*"), "<url>")
        .replace(Regex("(?i)token=\\S*"), "token=<redacted>")
