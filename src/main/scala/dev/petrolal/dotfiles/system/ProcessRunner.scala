// ProcessRunner.scala --- Process execution and subprocess management
// License: GPL-3.0-or-later
// Ported from ProcessRunner.java (docs/scala-migration-checklist.md step 3).
package dev.petrolal.dotfiles.system

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ExecutionException, Executors}
import scala.jdk.CollectionConverters.*

import dev.petrolal.dotfiles.core.Theme
import dev.petrolal.dotfiles.domain.{DotfileError, Result}

object ProcessRunner:

  /** Executes cmd, utilizing virtual threads to concurrently drain standard
    * output and standard error without blocking or pipe deadlocks, returning
    * the captured stdout on exit code 0. */
  def captureOutput(cmd: List[String]): Result[String] =
    try
      val executor = Executors.newVirtualThreadPerTaskExecutor()
      try
        val p = new ProcessBuilder(cmd.asJava).start()
        val stdoutFuture = executor.submit(() =>
          new String(p.getInputStream.readAllBytes(), StandardCharsets.UTF_8))
        val stderrFuture = executor.submit(() =>
          new String(p.getErrorStream.readAllBytes(), StandardCharsets.UTF_8))

        val exitCode = p.waitFor()
        val stdout = stdoutFuture.get()
        val stderr = stderrFuture.get()

        if exitCode != 0 then
          val binary = cmd.headOption.getOrElse("")
          val detail = if stderr.isBlank then binary else s"$binary: ${stderr.trim}"
          Result.err(DotfileError.CommandFailed(detail, exitCode))
        else Result.ok(stdout)
      finally executor.close()
    catch
      case e: java.io.IOException => Result.err(DotfileError.IoError(e.getMessage))
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Result.err(DotfileError.IoError("interrupted"))
      case e: ExecutionException =>
        val message = Option(e.getCause).map(_.getMessage).getOrElse(e.getMessage)
        Result.err(DotfileError.IoError(message))

  /** Runs cmd to completion, discarding output. Returns Result.Ok(0) on
    * success, or Result.Err with CommandFailed if the exit code is non-zero. */
  def run(cmd: List[String]): Result[Int] =
    try
      val p = new ProcessBuilder(cmd.asJava)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
      val exitCode = p.waitFor()
      if exitCode != 0 then
        val binary = cmd.headOption.getOrElse("")
        Result.err(DotfileError.CommandFailed(binary, exitCode))
      else Result.ok(exitCode)
    catch
      case e: java.io.IOException => Result.err(DotfileError.IoError(e.getMessage))
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Result.err(DotfileError.IoError("interrupted"))

  /** Fire-and-forget launch: start() without waitFor() genuinely detaches the
    * child process instead of blocking for its lifetime. */
  def spawnDetached(cmd: List[String]): Unit =
    spawnDetached(cmd, Map.empty)

  /** Same as spawnDetached(cmd), with additional environment variables merged
    * in on top of Theme.ENV (e.g. MANGOHUD=1). */
  def spawnDetached(cmd: List[String], extraEnv: Map[String, String]): Unit =
    try
      val pb = new ProcessBuilder(cmd.asJava)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
      pb.environment().putAll(Theme.ENV.asJava)
      pb.environment().putAll(extraEnv.asJava)
      pb.start()
    catch case _: java.io.IOException => () // best-effort: service reload is not fatal to deployment
