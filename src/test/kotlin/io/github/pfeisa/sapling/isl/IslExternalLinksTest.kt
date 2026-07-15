package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslExternalLinksTest {

    @Test
    fun acceptsHttpAndHttpsUrls() {
        assertTrue(isSafeExternalUrl("https://github.com/org/repo/pull/123"))
        assertTrue(isSafeExternalUrl("http://example.com/path?q=1"))
        // Scheme comparison is case-insensitive.
        assertTrue(isSafeExternalUrl("HTTPS://github.com/org/repo/pull/123"))
        // Surrounding whitespace is trimmed.
        assertTrue(isSafeExternalUrl("  https://github.com/x  "))
    }

    @Test
    fun rejectsDangerousSchemes() {
        assertFalse(isSafeExternalUrl("file:///etc/passwd"))
        assertFalse(isSafeExternalUrl("javascript:alert(1)"))
        assertFalse(isSafeExternalUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse("mailto is not a browser link", isSafeExternalUrl("mailto:x@y.z"))
    }

    @Test
    fun rejectsBlankRelativeAndMalformed() {
        assertFalse(isSafeExternalUrl(null))
        assertFalse(isSafeExternalUrl(""))
        assertFalse(isSafeExternalUrl("   "))
        assertFalse("relative path has no scheme", isSafeExternalUrl("foo/bar"))
        assertFalse("illegal character makes URI parsing throw", isSafeExternalUrl("http://exa mple.com"))
    }
}
