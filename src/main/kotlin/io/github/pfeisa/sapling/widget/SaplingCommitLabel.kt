package io.github.pfeisa.sapling.widget

/**
 * Status-bar label for the current commit `.`: the active bookmark name if present,
 * otherwise "<shortnode> <first line of description>" (node alone if the summary is blank).
 * Pure — inputs are strings already extracted off the EDT.
 */
fun commitLabel(activeBookmark: String?, shortNode: String, summaryFirstLine: String): String {
    if (!activeBookmark.isNullOrBlank()) return activeBookmark
    val summary = summaryFirstLine.trim()
    return if (summary.isEmpty()) shortNode else "$shortNode $summary"
}
