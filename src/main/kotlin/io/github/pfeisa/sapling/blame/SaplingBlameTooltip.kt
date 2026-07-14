package io.github.pfeisa.sapling.blame

/** Formats the concise annotation gutter tooltip. Platform-free (caller supplies the formatted date). */
object SaplingBlameTooltip {
    fun format(commit: SaplingBlameCommit, dateText: String): String {
        val header = "${commit.node.take(8)} — ${commit.author}, $dateText"
        val firstLine = commit.message.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine.isEmpty()) header else "$header\n$firstLine"
    }
}
