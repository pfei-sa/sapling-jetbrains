package io.github.pfeisa.sapling.isl

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Hosts the ISL webview. When JCEF is unavailable, shows a message panel instead of
 * crashing. The browser is a child Disposable of this panel.
 */
class IslBrowserPanel(
    private val project: Project,
    private val repoRoot: String,
) : Disposable {

    val component: JPanel = JPanel(BorderLayout())

    private val browser: JBCefBrowser? =
        if (JBCefApp.isSupported()) JBCefBrowser().also { Disposer.register(this, it) } else null

    @Volatile
    private var disposed = false

    private var bridge: IdeBridge? = null
    private var themeSync: ThemeSync? = null

    init {
        if (browser == null) {
            component.add(
                JBLabel("This IDE build has no JCEF runtime, so ISL cannot be embedded."),
                BorderLayout.CENTER,
            )
        } else {
            component.add(browser.component, BorderLayout.CENTER)
            val b = IdeBridge(project, browser, repoRoot)
            Disposer.register(this, b)
            bridge = b
            val ts = ThemeSync(browser)
            Disposer.register(this, ts)
            themeSync = ts
            browser.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
                override fun onLoadEnd(b: org.cef.browser.CefBrowser, f: org.cef.browser.CefFrame, code: Int) {
                    if (disposed || !f.isMain) return
                    ts.pushCurrentTheme()
                }
            }, browser.cefBrowser)
            browser.jbCefClient.addLifeSpanHandler(object : org.cef.handler.CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    b: org.cef.browser.CefBrowser,
                    f: org.cef.browser.CefFrame,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean {
                    // ISL surfaces external links (e.g. GitHub PR links) as target=_blank /
                    // window.open. Route them to the OS browser instead of a dead in-webview
                    // popup. Return true to cancel the popup either way; an unsafe/blocked URL
                    // is dropped and never logged with its content.
                    if (!disposed && isSafeExternalUrl(targetUrl)) {
                        ApplicationManager.getApplication().invokeLater {
                            if (!disposed) BrowserUtil.browse(targetUrl!!)
                        }
                    }
                    return true
                }
            }, browser.cefBrowser)
        }
    }

    val isJcefAvailable: Boolean get() = browser != null

    fun load(info: IslServerInfo) {
        if (disposed) return
        browser?.loadURL(info.url)
    }

    override fun dispose() {
        disposed = true
    }
}
