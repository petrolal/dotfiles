// ProcessRunner.java --- Process execution and subprocess management
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.system

import dev.petrolal.dotfiles.domain.DotfileError.CommandFailed
import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

object ProcessRunner {
    /** Shared across calls: a virtual-thread executor is cheap per task, but this
     * class is called repeatedly within a single process lifetime (e.g. the
     * notif-center and volume OSD loops), so one long-lived executor avoids
     * standing up and tearing down a pool on every invocation.  */
    private val ioExecutor = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Executes CMD, utilizing Virtual Threads to concurrently drain standard output
     * and standard error without blocking or pipe deadlocks, returning the captured
     * stdout on exit code 0.
     */
    @JvmStatic
    fun captureOutput(cmd: List<String>): Result<String> {
        try {
            val p = ProcessBuilder(cmd).start()
            val stdoutFuture: Future<String> = ioExecutor.submit(Callable {
                String(p.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            })
            val stderrFuture: Future<String> = ioExecutor.submit(Callable {
                String(p.errorStream.readAllBytes(), StandardCharsets.UTF_8)
            })

            val exitCode = p.waitFor()
            val stdout = stdoutFuture.get()
            val stderr = stderrFuture.get()

            if (exitCode != 0) {
                val binary = if (cmd.isEmpty()) "" else cmd.first()
                val detail = if (stderr.isBlank()) binary else "$binary: ${stderr.trim()}"
                return Result.err(CommandFailed(detail, exitCode))
            }
            return Result.ok(stdout)
        } catch (e: IOException) {
            return Result.err(IoError(e.message ?: ""))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return Result.err(IoError("interrupted"))
        } catch (e: ExecutionException) {
            val msg = e.cause?.message ?: e.message ?: ""
            return Result.err(IoError(msg))
        }
    }

    /**
     * Runs CMD to completion, discarding output. Returns Result.Ok(0) on success,
     * or Result.Err with CommandFailed if the exit code is non-zero.
     */
    @JvmStatic
    fun run(cmd: List<String>): Result<Int> {
        try {
            val p = ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exitCode = p.waitFor()
            if (exitCode != 0) {
                val binary = if (cmd.isEmpty()) "" else cmd.first()
                return Result.err(CommandFailed(binary, exitCode))
            }
            return Result.ok(exitCode)
        } catch (e: IOException) {
            return Result.err(IoError(e.message ?: ""))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return Result.err(IoError("interrupted"))
        }
    }

    /**
     * Fire-and-forget launch: start() without waitFor() genuinely detaches the child
     * process instead of blocking for its lifetime. `extraEnv` is merged in on
     * top of the inherited environment (e.g. MANGOHUD=1).
     */
    @JvmStatic
    @JvmOverloads
    fun spawnDetached(cmd: List<String>, extraEnv: Map<String, String> = emptyMap()) {
        try {
            val detachedCmd = if (cmd.firstOrNull() == "setsid") cmd else listOf("setsid") + cmd
            val pb = ProcessBuilder(detachedCmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            pb.environment().putAll(extraEnv)
            pb.start()
        } catch (_: IOException) {
            // best-effort: service reload is not fatal to deployment
        }
    }
}
