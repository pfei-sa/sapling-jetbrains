package io.github.pfeisa.sapling.realrepo

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** A throwaway native `.sl` repo with isolated identity and one seed commit. */
class SlTestRepo private constructor(val root: Path, private val home: Path) : AutoCloseable {

    /** Runs `sl <args>` in the repo under the isolated HOME. */
    fun sl(vararg args: String): CmdResult =
        IntegrationTools.exec(listOf("sl", *args), root, mapOf("HOME" to home.toString()))

    fun writeFile(relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    /** Full 40-char node of [rev] (default: current commit `.`). */
    fun node(rev: String = "."): String = sl("log", "-r", rev, "-T", "{node}").stdout.trim()

    override fun close() {
        IntegrationTools.deleteRecursively(root)
        IntegrationTools.deleteRecursively(home)
    }

    companion object {
        fun create(): SlTestRepo {
            val root = Files.createTempDirectory("sl-it-")
            val home = Files.createTempDirectory("sl-it-home-")
            IntegrationTools.exec(listOf("sl", "init", root.toString()), root, mapOf("HOME" to home.toString()))
            // Repo-local identity: works regardless of HOME, so the plugin's own bare
            // `sl commit` (console env, not this HOME) also has a username. Appended so any
            // config `sl init` wrote is preserved.
            Files.writeString(
                root.resolve(".sl").resolve("config"),
                "\n[ui]\nusername = Sapling IT <it@example.com>\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            )
            val repo = SlTestRepo(root, home)
            repo.writeFile("README.md", "seed\n")
            repo.sl("add", "README.md")
            repo.sl("commit", "-m", "seed")
            return repo
        }
    }
}
