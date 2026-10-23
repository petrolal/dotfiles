package dev.petrolal.dotfiles.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DotfileErrorTest:

  @Test
  def describeCoversEveryVariant(): Unit =
    assertEquals(
      "command failed (1): xfconf-query",
      DotfileError.CommandFailed("xfconf-query", 1).describe
    )
    assertEquals(
      "file not found: /tmp/missing",
      DotfileError.FileNotFound(Path.of("/tmp/missing")).describe
    )
    assertEquals(
      "I/O error: disk full",
      DotfileError.IoError("disk full").describe
    )
    assertEquals(
      "parse error: unexpected token",
      DotfileError.ParseError("unexpected token").describe
    )
