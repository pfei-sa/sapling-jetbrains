package io.github.pfeisa.sapling.realrepo

import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path

/** Result of a fixture-run process (distinct from the plugin's SaplingResult). */
data class CmdResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0
}

object IntegrationTools {

    val slAvailable: Boolean by lazy { toolExists("sl") }
    val gitAvailable: Boolean by lazy { toolExists("git") }

    /** Plain-JUnit skip helpers (Assume => reported as skipped, never failed). */
    fun assumeSl() = Assume.assumeTrue("`sl` not on PATH", slAvailable)
    fun assumeGit() = Assume.assumeTrue("`git` not on PATH", gitAvailable)
    fun assumeSlAndGit() {
        assumeSl(); assumeGit()
    }

    /**
     * Runs [command] in [workingDir] with [env] layered on top of the inherited environment
     * (so PATH is preserved; callers override only HOME). Output is redirected to temp files
     * to avoid pipe-buffer deadlock on larger outputs.
     */
    fun exec(command: List<String>, workingDir: Path, env: Map<String, String>): CmdResult {
        val outF = Files.createTempFile("it-out", ".txt")
        val errF = Files.createTempFile("it-err", ".txt")
        try {
            val pb = ProcessBuilder(command).directory(workingDir.toFile())
            pb.environment().putAll(env)
            pb.redirectOutput(outF.toFile())
            pb.redirectError(errF.toFile())
            val code = pb.start().waitFor()
            return CmdResult(code, Files.readString(outF), Files.readString(errF))
        } finally {
            Files.deleteIfExists(outF)
            Files.deleteIfExists(errF)
        }
    }

    fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    private fun toolExists(exe: String): Boolean {
        val dir = Files.createTempDirectory("tool-check")
        return try {
            exec(listOf(exe, "--version"), dir, emptyMap()).success
        } catch (e: Exception) {
            false
        } finally {
            deleteRecursively(dir)
        }
    }
}
