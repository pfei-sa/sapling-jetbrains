package io.github.pfeisa.sapling.util

/**
 * Reassembles newline-terminated lines from arbitrarily-chunked process output. [append] returns the
 * complete lines available so far (with the trailing `\n` stripped) and retains any partial final line;
 * [remainder] returns that partial line once the stream ends. Not thread-safe — used from a single
 * process-output reader thread.
 */
class SaplingLineBuffer {
    private val sb = StringBuilder()

    fun append(chunk: String): List<String> {
        sb.append(chunk)
        val lines = mutableListOf<String>()
        var idx = sb.indexOf("\n")
        while (idx >= 0) {
            lines.add(sb.substring(0, idx))
            sb.delete(0, idx + 1)
            idx = sb.indexOf("\n")
        }
        return lines
    }

    fun remainder(): String = sb.toString()
}
