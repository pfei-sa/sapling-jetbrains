package io.github.pfeisa.sapling.blame

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.vcsUtil.VcsUtil
import java.awt.datatransfer.StringSelection

/** Contributes Sapling actions to the annotation gutter right-click menu. */
class SaplingAnnotationGutterActionProvider : AnnotationGutterActionProvider {
    override fun createAction(annotation: FileAnnotation): AnAction {
        // Non-popup group inlines its children into the gutter menu. The actions self-hide
        // for non-Sapling annotations, so this provider is inert on git/other VCS gutters.
        val group = DefaultActionGroup()
        group.templatePresentation.isPopupGroup = false
        group.add(ShowInLogAction(annotation))
        group.add(ShowFileHistoryAction(annotation))
        group.add(CopyRevisionHashAction(annotation))
        return group
    }
}

private fun saplingAnnotation(a: FileAnnotation): SaplingFileAnnotation? = a as? SaplingFileAnnotation

private fun lineHashAt(e: AnActionEvent, annotation: FileAnnotation): String? {
    saplingAnnotation(annotation) ?: return null
    val line = e.getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR) ?: return null
    return annotation.getLineRevisionNumber(line)?.asString()?.takeIf { it.isNotEmpty() }
}

private class ShowInLogAction(private val annotation: FileAnnotation) : AnAction("Show in Log") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && lineHashAt(e, annotation) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ann = saplingAnnotation(annotation) ?: return
        val hash = lineHashAt(e, annotation) ?: return
        SaplingAnnotationNavigator.showInLog(project, ann.repoRoot, hash)
    }
}

private class ShowFileHistoryAction(private val annotation: FileAnnotation) : AnAction("Show File History") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && saplingAnnotation(annotation) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ann = saplingAnnotation(annotation) ?: return
        SaplingAnnotationNavigator.showFileHistory(project, VcsUtil.getFilePath(ann.file))
    }
}

private class CopyRevisionHashAction(private val annotation: FileAnnotation) : AnAction("Copy Revision Hash") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = lineHashAt(e, annotation) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val hash = lineHashAt(e, annotation) ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(hash))
    }
}
