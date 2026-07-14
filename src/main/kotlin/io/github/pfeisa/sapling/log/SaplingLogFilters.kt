package io.github.pfeisa.sapling.log

private val HEX = Regex("[0-9a-fA-F]+")

/**
 * Translates already-extracted VCS Log filter values into flag-form `sl log` arguments.
 *
 * Flag forms (`-u`, `-d`, `-k`, `-r`, positional `-- <path>`) are used deliberately over a
 * `grep('…')`/`user('…')` revset: each value becomes its **own** `argv` element, so filter text is
 * never interpolated into a revset and there is no injection surface. Hashes are validated hex before
 * use (belt-and-suspenders, since the framework already supplies hex). Multiple filter kinds AND
 * together; multiple `-u`/`-r` OR within their kind. Positional paths must come last (after `--`).
 *
 * [afterSpec]/[beforeSpec] are pre-formatted `sl` date strings (see SaplingLogProvider.formatSlDate);
 * kept as strings here so this function is pure and deterministically testable.
 */
fun buildLogFilterArgs(
    users: List<String>,
    afterSpec: String?,
    beforeSpec: String?,
    text: String?,
    paths: List<String>,
    hashes: List<String>,
): List<String> {
    val args = mutableListOf<String>()
    for (u in users) if (u.isNotBlank()) { args += "-u"; args += u }
    when {
        afterSpec != null && beforeSpec != null -> { args += "-d"; args += "$afterSpec to $beforeSpec" }
        afterSpec != null -> { args += "-d"; args += ">$afterSpec" }
        beforeSpec != null -> { args += "-d"; args += "<$beforeSpec" }
    }
    if (!text.isNullOrBlank()) { args += "-k"; args += text }
    for (h in hashes) if (h.matches(HEX)) { args += "-r"; args += h }
    val validPaths = paths.filter { it.isNotBlank() }
    if (validPaths.isNotEmpty()) { args += "--"; args += validPaths }
    return args
}
