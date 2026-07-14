package io.github.pfeisa.sapling.cli

import io.github.pfeisa.sapling.util.SaplingLineBuffer
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class SaplingResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val cancelled: Boolean,
) {
    val success: Boolean get() = exitCode == 0 && !timedOut && !cancelled
}

/** Like [SaplingResult] but carries raw stdout bytes (binary-safe); see [SaplingCli.runForBytes]. */
class SaplingByteResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
    val timedOut: Boolean,
    val cancelled: Boolean,
) {
    val success: Boolean get() = exitCode == 0 && !timedOut && !cancelled
}

/**
 * Result of [SaplingCli.runStreaming]: the process exit code plus the (tiny, fully-buffered) stderr
 * text — unlike stdout, which is streamed line-by-line and never buffered in full.
 */
data class SaplingStreamResult(
    val exitCode: Int,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Thin, background-only wrapper around the `sl` executable. Callers MUST invoke
 * [run] off the EDT.
 *
 * [executableOverride] is only for tests; production callers leave it null so the
 * path is resolved from [io.github.pfeisa.sapling.settings.SaplingSettings] on every
 * invocation (a long-lived provider must not freeze a stale path — see buildCommandLine).
 */
class SaplingCli(
    private val executableOverride: String? = null,
) {

    companion object {
        private val LOG = logger<SaplingCli>()
    }

    private val executablePath: String
        get() = executableOverride
            ?: com.intellij.openapi.application.ApplicationManager.getApplication()
                ?.let { io.github.pfeisa.sapling.settings.SaplingSettings.getInstance().executablePath }
            ?: "sl"

    fun buildCommandLine(
        workingDir: String,
        args: List<String>,
        charset: Charset = StandardCharsets.UTF_8,
    ): GeneralCommandLine =
        GeneralCommandLine(executablePath)
            .withParameters(args)
            .withWorkDirectory(workingDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(charset)

    @RequiresBackgroundThread
    fun run(
        workingDir: String,
        args: List<String>,
        indicator: ProgressIndicator? = null,
        timeoutMs: Int = 30_000,
    ): SaplingResult {
        val cmd = buildCommandLine(workingDir, args)
        return try {
            val handler = CapturingProcessHandler(cmd)
            val output =
                if (indicator != null) handler.runProcessWithProgressIndicator(indicator, timeoutMs)
                else handler.runProcess(timeoutMs)
            SaplingResult(output.exitCode, output.stdout, output.stderr, output.isTimeout, output.isCancelled)
        } catch (e: ProcessCanceledException) {
            throw e // control-flow exception — never swallow
        } catch (e: ExecutionException) {
            LOG.warn("Failed to start sl ${args.joinToString(" ")}", e)
            SaplingResult(exitCode = -1, stdout = "", stderr = e.message ?: "failed to start sl", timedOut = false, cancelled = false)
        }
    }

    /**
     * Runs `sl` capturing stdout as raw bytes (binary-safe). Decodes with ISO-8859-1,
     * a 1:1 byte<->char map, so the captured String round-trips back to the exact bytes
     * `sl` emitted — reusing the same cancellable [CapturingProcessHandler] path as [run].
     */
    @RequiresBackgroundThread
    fun runForBytes(
        workingDir: String,
        args: List<String>,
        indicator: ProgressIndicator? = null,
        timeoutMs: Int = 30_000,
    ): SaplingByteResult {
        val cmd = buildCommandLine(workingDir, args, StandardCharsets.ISO_8859_1)
        return try {
            val handler = CapturingProcessHandler(cmd)
            val output =
                if (indicator != null) handler.runProcessWithProgressIndicator(indicator, timeoutMs)
                else handler.runProcess(timeoutMs)
            SaplingByteResult(
                output.exitCode,
                output.stdout.toByteArray(StandardCharsets.ISO_8859_1),
                output.stderr,
                output.isTimeout,
                output.isCancelled,
            )
        } catch (e: ProcessCanceledException) {
            throw e // control-flow exception — never swallow
        } catch (e: ExecutionException) {
            LOG.warn("Failed to start sl ${args.joinToString(" ")}", e)
            SaplingByteResult(exitCode = -1, stdout = ByteArray(0), stderr = e.message ?: "failed to start sl", timedOut = false, cancelled = false)
        }
    }

    /**
     * Runs `sl` and streams stdout **line by line** to [onLine] as the process emits it — so callers
     * that iterate large output (e.g. full `sl log`) never buffer it all in memory. Returns the exit
     * code plus the accumulated stderr (small — buffered in full, unlike stdout — mirroring the
     * `${result.stderr}` convention every other method in this class uses for error messages).
     * Cancellation: polls the indicator (or the ambient [ProgressManager]) and, on cancel, destroys
     * the process and rethrows [ProcessCanceledException].
     *
     * No fixed timeout — the process ends when its output does; cancellation is the interrupt. [onLine]
     * is invoked on the process-output reader thread; keep it cheap and thread-appropriate.
     */
    @RequiresBackgroundThread
    fun runStreaming(
        workingDir: String,
        args: List<String>,
        indicator: ProgressIndicator? = null,
        onLine: (String) -> Unit,
    ): SaplingStreamResult {
        val cmd = buildCommandLine(workingDir, args)
        val handler = try {
            OSProcessHandler(cmd)
        } catch (e: ExecutionException) {
            LOG.warn("Failed to start sl ${args.joinToString(" ")}", e)
            return SaplingStreamResult(exitCode = -1, stderr = e.message ?: "failed to start sl")
        }
        val buffer = SaplingLineBuffer()
        val stderr = StringBuilder()
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType === ProcessOutputTypes.STDOUT) {
                    buffer.append(event.text).forEach(onLine)
                } else if (outputType === ProcessOutputTypes.STDERR) {
                    stderr.append(event.text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                buffer.remainder().takeIf { it.isNotBlank() }?.let(onLine)
            }
        })
        handler.startNotify()
        try {
            while (!handler.waitFor(50)) {
                if (indicator != null) indicator.checkCanceled() else ProgressManager.checkCanceled()
            }
        } catch (e: ProcessCanceledException) {
            handler.destroyProcess()
            throw e // control-flow exception — never swallow
        }
        return SaplingStreamResult(handler.exitCode ?: -1, stderr.toString())
    }
}
