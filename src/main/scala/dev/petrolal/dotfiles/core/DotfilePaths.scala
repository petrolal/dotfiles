// DotfilePaths.scala --- Path and environment utilities
// License: GPL-3.0-or-later
// Ported from DotfilePaths.java (docs/scala-migration-checklist.md step 2).
package dev.petrolal.dotfiles.core

import java.nio.file.{Files, Path, Paths}
import scala.util.Try

object DotfilePaths:

  def userHomeDirectory(): Path =
    val home = Option(System.getenv("HOME")).getOrElse(System.getProperty("user.home"))
    Paths.get(home)

  private def normalizeDirParent(dir: Path): Path =
    val last = dir.getFileName
    if last != null && (last.toString == "src" || last.toString == "bin") then
      Option(dir.getParent).getOrElse(dir)
    else dir

  def findDotfilesRoot(): Path =
    Option(System.getenv("DOTFILES_DIR")) match
      case Some(envDir) =>
        val p = Paths.get(envDir)
        if Files.exists(p) then Try(p.toRealPath()).getOrElse(p.toAbsolutePath.normalize())
        else fromExecutablePathOrCwd()
      case None => fromExecutablePathOrCwd()

  private def fromExecutablePathOrCwd(): Path =
    val exePath = Option(ProcessHandle.current().info().command().orElse(null))
    val resolved = exePath.flatMap { raw =>
      val exe = Paths.get(raw)
      if Files.exists(exe) then
        Try {
          val real = exe.toRealPath()
          Option(real.getParent).map(normalizeDirParent)
        }.toOption.flatten
      else None
    }
    resolved.getOrElse(cwd())

  private def cwd(): Path = Paths.get("").toAbsolutePath.normalize()

  def commandExists(cmd: String): Boolean =
    if cmd == null || cmd.isEmpty then false
    else if cmd.indexOf('/') >= 0 then
      val p = Paths.get(cmd)
      Files.isRegularFile(p) && Files.isExecutable(p)
    else
      Option(System.getenv("PATH")) match
        case None => false
        case Some(pathEnv) =>
          pathEnv
            .split(":")
            .exists { dir =>
              dir.nonEmpty && {
                val candidate = Paths.get(dir).resolve(cmd)
                Files.isRegularFile(candidate) && Files.isExecutable(candidate)
              }
            }
