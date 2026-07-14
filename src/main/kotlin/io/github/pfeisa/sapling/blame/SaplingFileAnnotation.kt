package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.DateFormatUtil
import com.intellij.xml.util.XmlStringUtil
import java.nio.file.Path
import java.util.Date

/** Renders `sl annotate` output in the editor gutter, with enriched tooltips and Log navigation. */
class SaplingFileAnnotation(
    private val project: Project,
    private val annotatedFile: VirtualFile,
    private val lines: List<SaplingBlameLine>,
    private val currentRevision: SaplingRevisionNumber?,
    private val commits: List<SaplingBlameCommit>,
    private val revisions: List<VcsFileRevision>,
    internal val repoRoot: Path,
) : FileAnnotation(project) {

    private val content: String = lines.joinToString("") { it.content }
    private val commitByNode: Map<String, SaplingBlameCommit> = commits.associateBy { it.node }

    private val revisionAspect = object : LineAnnotationAspectAdapter(
        LineAnnotationAspect.REVISION, LineAnnotationAspect.REVISION, true
    ) {
        override fun getValue(line: Int): String = lines.getOrNull(line)?.node?.take(8) ?: ""

        // Left-click on the revision link → open the VCS Log tool window at that commit.
        override fun showAffectedPaths(line: Int) {
            val node = lines.getOrNull(line)?.node?.takeIf { it.isNotEmpty() } ?: return
            SaplingAnnotationNavigator.showInLog(project, repoRoot, node)
        }
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
        val commit = commitByNode[l.node]
            ?: return "${l.node.take(8)} — ${l.author}" // degraded: no batched-log metadata
        val text = SaplingBlameTooltip.format(
            commit,
            DateFormatUtil.formatDateTime(Date(commit.dateEpochSeconds * 1000)),
        )
        return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(text).replace("\n", "<br/>"))
    }

    override fun getAnnotationSourceSwitcher(): AnnotationSourceSwitcher? = null

    // Age-based gutter colouring / ordering: the distinct commits in this annotation, newest first.
    override fun getRevisions(): List<VcsFileRevision> = revisions
}
