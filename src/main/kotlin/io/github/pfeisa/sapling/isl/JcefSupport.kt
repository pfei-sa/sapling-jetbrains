package io.github.pfeisa.sapling.isl

/**
 * Linkage-safe JCEF availability probe. `JBCefApp.isSupported()` is the official gate, but
 * some IDE processes ship without the JCEF classes at all (e.g. the Rider / remote-dev
 * backend), so merely resolving the `JBCefApp` reference throws `NoClassDefFoundError` —
 * the exact crash the gate is meant to prevent (issue #3). Every JCEF entry point must
 * check this instead of calling `JBCefApp.isSupported()` directly; the direct reference
 * below is safe because resolution is lazy and the resulting `LinkageError` is caught here.
 */
object JcefSupport {
    val isAvailable: Boolean by lazy {
        try {
            com.intellij.ui.jcef.JBCefApp.isSupported()
        } catch (e: LinkageError) {
            false
        }
    }
}
