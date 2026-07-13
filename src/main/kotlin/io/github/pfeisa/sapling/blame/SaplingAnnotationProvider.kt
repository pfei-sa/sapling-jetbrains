package io.github.pfeisa.sapling.blame

import io.github.pfeisa.sapling.changes.SaplingRevisionNumber
import io.github.pfeisa.sapling.cli.SaplingCli
import io.github.pfeisa.sapling.util.SaplingPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile

class SaplingAnnotationProvider(
    private val project: Project,
    private val cli: SaplingCli = SaplingCli(),
) : AnnotationProvider {

    override fun annotate(file: VirtualFile): FileAnnotation {
        val root = SaplingPaths.repoRoot(file.toNioPath())
            ?: throw VcsException("Not inside a Sapling repository: ${file.path}")
        val relative = SaplingPaths.relative(root, file.toNioPath())
            ?: throw VcsException("File is outside the repository: ${file.path}")

        val result = cli.run(
            root.toString(),
            // `--` terminates option parsing so a file named like a flag can't inject `sl` options.
            listOf("annotate", "-Tjson", "--", relative),
        )
        if (!result.success) throw VcsException("sl annotate failed: ${result.stderr}")

        val lines = parseSaplingAnnotate(result.stdout)
        val currentNode = cli.run(root.toString(), listOf("log", "-r", ".", "-T", "{node}\n"))
            .takeIf { it.success }?.stdout?.trim()?.ifEmpty { null }
        return SaplingFileAnnotation(project, file, lines, currentNode?.let { SaplingRevisionNumber(it) })
    }

    // TODO(v0.1): annotate always uses the working-copy revision; per-revision blame (sl annotate --rev) is a follow-up.
    override fun annotate(file: VirtualFile, revision: VcsFileRevision): FileAnnotation = annotate(file)
}
