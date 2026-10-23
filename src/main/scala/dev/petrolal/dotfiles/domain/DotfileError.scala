// DotfileError.scala --- Typed domain errors for Result[T]
// License: GPL-3.0-or-later
// Ported from DotfileError.java (docs/scala-migration-checklist.md step 1).
package dev.petrolal.dotfiles.domain

import java.nio.file.Path

enum DotfileError:
  case CommandFailed(command: String, exitCode: Int)
  case FileNotFound(path: Path)
  case IoError(message: String)
  case ParseError(message: String)

  def describe: String = this match
    case DotfileError.CommandFailed(command, exitCode) =>
      s"command failed ($exitCode): $command"
    case DotfileError.FileNotFound(path) => s"file not found: $path"
    case DotfileError.IoError(message)   => s"I/O error: $message"
    case DotfileError.ParseError(message) => s"parse error: $message"
