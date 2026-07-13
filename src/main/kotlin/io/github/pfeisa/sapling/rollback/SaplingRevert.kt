package io.github.pfeisa.sapling.rollback

/**
 * Args for `sl revert` of the given repo-relative paths. `--no-backup` (verified flag `-C`)
 * suppresses `.orig` litter — an IDE revert should be clean. `--` ends option parsing so a
 * path beginning with `-` is never read as a flag.
 */
fun revertArgs(relativePaths: List<String>): List<String> =
    listOf("revert", "--no-backup", "--") + relativePaths
