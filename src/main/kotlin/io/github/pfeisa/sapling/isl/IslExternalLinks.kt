package io.github.pfeisa.sapling.isl

import java.net.URI

/**
 * True only for links safe to hand to the OS default browser: absolute
 * http/https URLs. Rejects blank, relative, malformed, and dangerous schemes
 * (file:, javascript:, data:, mailto:, custom) so an untrusted webview link
 * cannot invoke an arbitrary OS handler.
 */
internal fun isSafeExternalUrl(url: String?): Boolean {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return false
    val scheme = runCatching { URI(raw).scheme }.getOrNull()?.lowercase() ?: return false
    return scheme == "http" || scheme == "https"
}
