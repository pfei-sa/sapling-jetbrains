package io.github.pfeisa.sapling.isl

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser

/**
 * Pushes IDE light/dark changes into ISL as `onIDEThemeChange` events and seeds
 * `window.__ideTheme` (read by __IdeBridge.getIDETheme).
 */
class ThemeSync(private val browser: JBCefBrowser) : Disposable {

    @Volatile
    private var disposed = false

    init {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { pushCurrentTheme() })
    }

    fun pushCurrentTheme() {
        if (disposed) return
        val theme = themeName(isDark = !JBColor.isBright())
        val js =
            "window.__ideTheme='$theme';" +
                "window.dispatchEvent(new CustomEvent('onIDEThemeChange',{detail:'$theme'}));"
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    override fun dispose() {
        disposed = true
    }
}
