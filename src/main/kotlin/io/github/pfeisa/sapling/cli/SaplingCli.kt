package io.github.pfeisa.sapling.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
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
}
