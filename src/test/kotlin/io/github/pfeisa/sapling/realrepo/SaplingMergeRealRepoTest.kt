package io.github.pfeisa.sapling.realrepo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test for the 3-way merge provider's underlying sl commands.
 *
 * Deviation from task-5 brief: the brief called for `parseUnresolvedConflicts` from
 * the conflict package (Task 6). Since Task 6 has not yet run, this test is self-contained:
 * it parses `sl resolve --list -Tjson` output inline by checking for `"status": "U"` strings
 * and path containment, rather than delegating to an as-yet-nonexistent helper.
 *
 * Open question recorded here: does `p2()` resolve during a rebase conflict?
 * OBSERVED: YES — `sl log -r p2()` and `sl cat -r p2()` both succeed mid-rebase,
 * returning the "theirs" content (the commit being rebased). All three sides
 * (ancestor/p1/p2) are fully available during a rebase conflict in this sl version.
 */
class SaplingMergeRealRepoTest {

    @Test
    fun conflictExposesSidesAndMarkResolves() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            // Set up: base commit with f.txt = "base\n"
            repo.writeFile("f.txt", "base\n")
            repo.sl("add", "f.txt")
            repo.sl("commit", "-m", "base")
            val base = repo.node()

            // "ours" stack: edit f.txt to "ours\n"
            repo.writeFile("f.txt", "ours\n")
            repo.sl("commit", "-m", "ours")
            val ours = repo.node()

            // "theirs" stack: from base, edit f.txt to "theirs\n"
            repo.sl("goto", "-r", base)
            repo.writeFile("f.txt", "theirs\n")
            repo.sl("commit", "-m", "theirs")
            val theirs = repo.node()

            // Rebase theirs onto ours → conflict on f.txt (both edited the same line)
            // sl exits non-zero on conflict; that's expected — we proceed.
            repo.sl("rebase", "-r", theirs, "-d", ours)

            // --- Assert conflict is visible via sl resolve --list -Tjson ---
            val listResult = repo.sl("resolve", "--list", "-Tjson")
            assertTrue(
                "sl resolve --list should succeed: ${listResult.stderr}",
                listResult.success || listResult.stdout.contains("\"path\""),
            )
            // Self-contained parse: look for "f.txt" with status "U" (Unresolved)
            val unresolvedPaths = parseUnresolvedPaths(listResult.stdout)
            assertTrue(
                "expected f.txt to be unresolved, got: ${listResult.stdout}",
                unresolvedPaths.contains("f.txt"),
            )

            // --- Verify the three sl cat sides (documents the open question) ---
            // p1() = ours (destination of the rebase = working-copy "local" parent)
            val p1Cat = repo.sl("cat", "-r", "p1()", "--", "f.txt")
            assertTrue("p1() cat should succeed during conflict: ${p1Cat.stderr}", p1Cat.success)
            assertEquals("p1() should be 'ours' content", "ours\n", p1Cat.stdout)

            // p2() = theirs (the commit being rebased = working-copy "other" parent)
            // OPEN QUESTION RESULT: p2() IS populated during a rebase conflict.
            val p2Cat = repo.sl("cat", "-r", "p2()", "--", "f.txt")
            assertTrue("p2() cat should succeed during conflict: ${p2Cat.stderr}", p2Cat.success)
            assertEquals("p2() should be 'theirs' content", "theirs\n", p2Cat.stdout)

            // ancestor(p1(),p2()) = base (common ancestor)
            val ancestorCat = repo.sl("cat", "-r", "ancestor(p1(),p2())", "--", "f.txt")
            assertTrue("ancestor cat should succeed: ${ancestorCat.stderr}", ancestorCat.success)
            assertEquals("ancestor should be 'base' content", "base\n", ancestorCat.stdout)

            // --- Resolve: write merged content and mark resolved ---
            repo.writeFile("f.txt", "merged\n")
            val markResult = repo.sl("resolve", "--mark", "--", "f.txt")
            assertTrue("resolve --mark failed: ${markResult.stderr}", markResult.success)

            // Assert f.txt is no longer unresolved
            val afterResult = repo.sl("resolve", "--list", "-Tjson")
            val afterUnresolved = parseUnresolvedPaths(afterResult.stdout)
            assertTrue(
                "f.txt should be resolved after --mark, got: ${afterResult.stdout}",
                !afterUnresolved.contains("f.txt"),
            )
        }
    }

    /**
     * Self-contained parse of `sl resolve --list -Tjson` output.
     * Returns the set of paths whose status is "U" (Unresolved).
     * Format:
     * ```json
     * [{ "path": "f.txt", "status": "U" }, ...]
     * ```
     * Avoids a dependency on `parseUnresolvedConflicts` from the conflict package (Task 6).
     */
    private fun parseUnresolvedPaths(json: String): Set<String> {
        val result = mutableSetOf<String>()
        // Extract all { "path": "...", "status": "..." } objects and collect those with status "U".
        // Simple regex-free parse: split into blocks by "}" to find each entry.
        val blocks = json.split("}")
        for (block in blocks) {
            if (!block.contains("\"status\": \"U\"")) continue
            val pathMatch = Regex("\"path\":\\s*\"([^\"]+)\"").find(block) ?: continue
            result.add(pathMatch.groupValues[1])
        }
        return result
    }
}
