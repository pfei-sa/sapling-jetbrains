package io.github.pfeisa.sapling.realrepo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.cli.SaplingCli
import java.util.Collections

/**
 * Drives the real [SaplingCli.runStreaming] (OSProcessHandler + line buffering + exit code) against
 * a real `sl` repo — [SaplingLogStreamRealRepoTest] already covers the NUL-delimited template/parser
 * contract via a separate buffered `ProcessBuilder`, but neither test previously exercised
 * `runStreaming`/`readAllHashes` themselves. This one does.
 */
class SaplingCliStreamingRealRepoTest : BasePlatformTestCase() {

    fun testRunStreamingCollectsEveryCommitNode() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "1\n"); repo.sl("add", "a.txt"); repo.sl("commit", "-m", "c1")
            repo.writeFile("a.txt", "2\n"); repo.sl("commit", "-m", "c2")

            val expectedNodes = repo.sl("log", "-T", "{node}\n").stdout.trim().lines().filter { it.isNotBlank() }
            assertEquals("expected seed + c1 + c2", 3, expectedNodes.size)

            val collected = Collections.synchronizedList(mutableListOf<String>())
            val cli = SaplingCli("sl")
            val result = cli.runStreaming(repo.root.toString(), listOf("log", "-T", "{node}\n"), null) { line ->
                collected.add(line)
            }

            assertTrue("runStreaming failed: ${result.stderr}", result.success)
            assertEquals(0, result.exitCode)
            assertEquals(expectedNodes.size, collected.size)
            assertEquals(expectedNodes, collected.toList())
        }
    }
}
