package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.DateFormatUtil
import java.util.Date

/** Renders `sl annotate` output in the editor gutter. */
class SaplingFileAnnotation(
    project: Project,
    private val annotatedFile: VirtualFile,
    private val lines: List<SaplingBlameLine>,
    private val currentRevision: SaplingRevisionNumber?,
) : FileAnnotation(project) {

    private val content: String = lines.joinToString("") { it.content }

    private val revisionAspect = object : LineAnnotationAspectAdapter(
        LineAnnotationAspect.REVISION, LineAnnotationAspect.REVISION, true
    ) {
        override fun getValue(line: Int): String = lines.getOrNull(line)?.node?.take(8) ?: ""
        override fun showAffectedPaths(line: Int) {}
    }

    private val authorAspect = object : LineAnnotationAspectAdapter(
        LineAnnotationAspect.AUTHOR, LineAnnotationAspect.AUTHOR, true
    ) {
        override fun getValue(line: Int): String = lines.getOrNull(line)?.author ?: ""
        override fun showAffectedPaths(line: Int) {}
    }

    private val dateAspect = object : LineAnnotationAspectAdapter(
        LineAnnotationAspect.DATE, LineAnnotationAspect.DATE, true
    ) {
        override fun getValue(line: Int): String =
            lines.getOrNull(line)?.let { DateFormatUtil.formatDate(Date(it.dateEpochSeconds * 1000)) } ?: ""
        override fun showAffectedPaths(line: Int) {}
    }

    override fun getAnnotatedContent(): String = content
    override fun getLineCount(): Int = lines.size
    override fun getAspects(): Array<LineAnnotationAspect> = arrayOf(revisionAspect, authorAspect, dateAspect)

    override fun getLineRevisionNumber(lineNumber: Int): VcsRevisionNumber? =
        lines.getOrNull(lineNumber)?.let { SaplingRevisionNumber(it.node) }

    override fun getLineDate(lineNumber: Int): Date? =
        lines.getOrNull(lineNumber)?.let { Date(it.dateEpochSeconds * 1000) }

    // Correlates the annotation with its editor/document so the platform can detect
    // base-revision staleness and drive gutter refresh.
    override fun getFile(): VirtualFile = annotatedFile

    override fun getCurrentRevision(): VcsRevisionNumber? = currentRevision

    override fun getToolTip(lineNumber: Int): String {
        val l = lines.getOrNull(lineNumber) ?: return ""
        return "${l.node.take(8)} — ${l.author}"
    }

    override fun getAnnotationSourceSwitcher(): AnnotationSourceSwitcher? = null
    override fun getRevisions(): List<com.intellij.openapi.vcs.history.VcsFileRevision>? = null
}
