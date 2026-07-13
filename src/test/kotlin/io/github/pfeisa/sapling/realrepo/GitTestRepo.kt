package io.github.pfeisa.sapling.realrepo

import java.nio.file.Files
import java.nio.file.Path

/** A throwaway plain git repo with one seed commit (no Sapling metadata). */
class GitTestRepo private constructor(val root: Path) : AutoCloseable {

    /** Runs `git <args>` with an inline identity (no global git config dependency). */
    fun git(vararg args: String): CmdResult =
        IntegrationTools.exec(
            listOf("git", "-c", "user.name=Git IT", "-c", "user.email=it@example.com", *args),
            root, emptyMap(),
        )

    fun writeFile(relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    override fun close() = IntegrationTools.deleteRecursively(root)

    companion object {
        fun create(): GitTestRepo {
            val root = Files.createTempDirectory("git-it-")
            IntegrationTools.exec(listOf("git", "init", "-q"), root, emptyMap())
            val repo = GitTestRepo(root)
            repo.writeFile("f.txt", "x\n")
            repo.git("add", "f.txt")
            repo.git("commit", "-qm", "init")
            return repo
        }
    }
}
