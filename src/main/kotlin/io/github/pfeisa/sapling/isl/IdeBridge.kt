package io.github.pfeisa.sapling.isl

import io.github.pfeisa.sapling.changes.changeForComparison
import io.github.pfeisa.sapling.changes.classifyForComparison
import io.github.pfeisa.sapling.util.SaplingNotifications
import io.github.pfeisa.sapling.util.resolveWithinRepoLexical as resolveWithinRepoLexicalPure
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Installs `window.__IdeBridge` so the androidStudio ISL platform can drive the IDE:
 * open files in the editor, open native diffs, and copy to the clipboard. Payloads are treated as untrusted.
 */
class IdeBridge(
    private val project: Project,
    private val browser: JBCefBrowser,
    private val repoRoot: String,
) : Disposable {

    companion object {
        private val LOG = logger<IdeBridge>()
    }

    @Volatile
    private var disposed = false

    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)

    init {
        Disposer.register(this, query)
        query.addHandler { arg ->
            handle(arg)
            null
        }
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                // Guard the late JS callback: a page load can complete after this bridge is
                // disposed (it is torn down before the browser), so don't script a dead browser.
                if (disposed || !frame.isMain) return
                cefBrowser.executeJavaScript(installScript(), cefBrowser.url, 0)
            }
        }, browser.cefBrowser)
    }

    private fun installScript(): String {
        val openCall = query.inject("JSON.stringify({t:'open',path:path,line:line})")
        val copyCall = query.inject("JSON.stringify({t:'copy',data:data})")
        val diffCall = query.inject("JSON.stringify({t:'diff',path:path,comparison:comparison})")
        return """
            window.__IdeBridge = {
              openFileInAndroidStudio: function(path, line, col) { $openCall },
              clipboardCopy: function(data) { $copyCall },
              getIDETheme: function() { return window.__ideTheme || 'dark'; }
            };
            if (window.islPlatform) {
              window.islPlatform.openDiff = function(path, comparison) { $diffCall };
            }
        """.trimIndent()
    }

    private fun handle(arg: String?) {
        val raw = arg ?: return
        val msg = parseBridgeMessage(raw)
        if (msg == null) {
            LOG.warn("Ignoring malformed __IdeBridge payload")
            return
        }
        when (msg.t) {
            "open" -> msg.path?.let { openFile(it, msg.line) }
            "copy" -> msg.data?.let { copy(it) }
            "diff" -> {
                val p = msg.path
                val c = msg.comparison
                if (p != null && c != null) openDiff(p, c)
            }
        }
    }

    private fun openFile(relativePath: String, line: Int?) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            val realTarget = resolveWithinRepoReal(relativePath)
            if (realTarget == null) {
                LOG.warn("Ignoring __IdeBridge open request outside the repository root")
                return@invokeLater
            }
            // 2025.1+ no longer wraps invokeLater callbacks in the write-intent lock, and the VFS
            // refresh + editor open below touch the model, so acquire the lock explicitly.
            WriteIntentReadAction.run {
                val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(realTarget) ?: return@run
                val descriptor = OpenFileDescriptor(project, vf, ((line ?: 1) - 1).coerceAtLeast(0), 0)
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
            }
        }
    }

    /** Lexical containment only (rejects absolute + `../`-escaping paths). Does NOT require existence,
     *  so a committed diff of a file absent from the working copy still validates. Delegates to the
     *  pure, unit-tested [resolveWithinRepoLexicalPure]. */
    private fun resolveWithinRepoLexical(relativePath: String): Path? =
        resolveWithinRepoLexicalPure(repoRoot, relativePath)

    /** Lexical check plus symlink resolution + existence — used where a real on-disk file is opened. */
    private fun resolveWithinRepoReal(relativePath: String): Path? {
        val lexical = resolveWithinRepoLexical(relativePath) ?: return null
        val realRoot = runCatching { Paths.get(repoRoot).normalize().toRealPath() }.getOrNull() ?: return null
        val realTarget = runCatching { lexical.toRealPath() }.getOrNull() ?: return null
        return if (realTarget.startsWith(realRoot)) realTarget else null
    }

    private fun openDiff(relativePath: String, comparison: Comparison) {
        if (resolveWithinRepoLexical(relativePath) == null) {
            LOG.warn("Ignoring __IdeBridge diff request outside the repository root")
            return
        }
        val type = comparison.type
        if (type != "Uncommitted" && type != "Commit") {
            notify("Opening ${StringUtil.escapeXmlEntities(type)} comparisons in the IDE isn't supported yet.")
            return
        }
        // Classification runs `sl status` (off-EDT), then the diff opens on the EDT. The diff
        // framework loads each ContentRevision's content off-EDT under its own progress indicator.
        ApplicationManager.getApplication().executeOnPooledThread {
            if (disposed) return@executeOnPooledThread
            val root = Paths.get(repoRoot)
            val entry = classifyForComparison(type, comparison.hash, root, relativePath)
            val change = entry?.let {
                changeForComparison(type, comparison.hash, it.status, it.copySource, root, relativePath)
            }
            // The working-copy (after) side of an Uncommitted MODIFIED/ADDED/UNTRACKED diff reads a
            // real on-disk file (same as openFile), so it needs the symlink-resolving real guard, not
            // just the lexical one already checked above. Commit diffs and Uncommitted
            // REMOVED/MISSING stay lexical-only: the file may legitimately be absent from disk.
            if (type == "Uncommitted" && change?.afterRevision != null && resolveWithinRepoReal(relativePath) == null) {
                LOG.warn("Ignoring __IdeBridge diff request resolving outside the repository root")
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (change == null) {
                    notify("No diff available for ${StringUtil.escapeXmlEntities(relativePath)}.")
                } else {
                    ShowDiffAction.showDiffForChange(project, listOf(change))
                }
            }
        }
    }

    private fun notify(message: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) SaplingNotifications.info(project, message)
        }
    }

    private fun copy(data: String) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            CopyPasteManager.getInstance().setContents(StringSelection(data))
        }
    }

    override fun dispose() {
        disposed = true
    }
}
