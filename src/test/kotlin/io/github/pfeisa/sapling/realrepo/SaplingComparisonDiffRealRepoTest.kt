package io.github.pfeisa.sapling.realrepo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.pfeisa.sapling.changes.changesForComparison
import io.github.pfeisa.sapling.changes.collectComparisonEntries
import io.github.pfeisa.sapling.changes.indexOfComparisonChange
import io.github.pfeisa.sapling.cli.SaplingCli

/**
 * The whole-comparison listing behind the diff viewer's next/previous-file navigation: clicking one
 * file in ISL has to yield every file of that comparison, positioned on the clicked one. Exercises
 * the real `sl status` invocation [collectComparisonEntries] builds — the scoped, single-file call
 * it replaced would have returned exactly one entry here.
 */
class SaplingComparisonDiffRealRepoTest : BasePlatformTestCase() {

    fun testCommitComparisonListsEveryFileInTheCommit() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "a\n")
            repo.writeFile("dir/b.txt", "b\n")
            repo.writeFile("README.md", "seed\nedited\n")
            repo.sl("add", "a.txt", "dir/b.txt")
            repo.sl("commit", "-m", "three files")
            val node = repo.node(".")
            val cli = SaplingCli("sl")

            val entries = collectComparisonEntries("Commit", node, repo.root, cli)
            assertEquals(
                listOf("README.md", "a.txt", "dir/b.txt"),
                entries.map { it.path }.sorted(),
            )

            val changes = changesForComparison("Commit", node, entries, repo.root, cli)
            assertEquals("every entry maps to a change", 3, changes.size)

            // Clicking any one file resolves to its own slot in the multi-file list.
            val index = indexOfComparisonChange(changes, "dir/b.txt")
            assertTrue("clicked file is in the list", index >= 0)
            assertEquals("dir/b.txt", changes[index].path)
        }
    }

    fun testUncommittedComparisonListsEveryDirtyFile() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("README.md", "seed\ndirty\n")
            repo.writeFile("untracked.txt", "new\n")
            val cli = SaplingCli("sl")

            val entries = collectComparisonEntries("Uncommitted", null, repo.root, cli)
            val changes = changesForComparison("Uncommitted", null, entries, repo.root, cli)

            assertEquals(
                listOf("README.md", "untracked.txt"),
                changes.map { it.path }.sorted(),
            )
            assertTrue(indexOfComparisonChange(changes, "untracked.txt") >= 0)
        }
    }

    fun testRenameCommitYieldsOneEntryReachableFromEitherPath() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.sl("mv", "README.md", "DOCS.md")
            repo.sl("commit", "-m", "rename readme")
            val node = repo.node(".")
            val cli = SaplingCli("sl")

            val entries = collectComparisonEntries("Commit", node, repo.root, cli)
            val changes = changesForComparison("Commit", node, entries, repo.root, cli)

            assertEquals("the rename collapses to one file", listOf("DOCS.md"), changes.map { it.path })
            assertEquals("clicking the new path finds it", 0, indexOfComparisonChange(changes, "DOCS.md"))
            assertEquals("clicking the old path finds it too", 0, indexOfComparisonChange(changes, "README.md"))
        }
    }

    fun testHeadComparisonOmitsUntrackedNoiseButKeepsCommittedFiles() {
        if (!IntegrationTools.slAvailable) return
        SlTestRepo.create().use { repo ->
            repo.writeFile("a.txt", "a\n")
            repo.sl("add", "a.txt")
            repo.sl("commit", "-m", "add a")
            // Junk the IDE/user drops in the tree; ISL's dot-commit file list never shows these.
            repo.writeFile(".idea/workspace.xml", "<x/>\n")
            repo.writeFile("notes.txt", "scratch\n")
            val cli = SaplingCli("sl")

            val entries = collectComparisonEntries("Head", null, repo.root, cli)
            assertTrue(
                "sl still reports the untracked files",
                entries.any { it.path == "notes.txt" },
            )

            val changes = changesForComparison("Head", null, entries, repo.root, cli)
            assertEquals("only the committed file is navigable", listOf("a.txt"), changes.map { it.path })
        }
    }
}
