package dev.petrolal.dotfiles.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CliTest:

  @Test
  def helpReturnsZero(): Unit =
    assertEquals(0, Cli.run(Array("--help")))
    assertEquals(0, Cli.run(Array("-h")))

  @Test
  def versionReturnsZero(): Unit =
    assertEquals(0, Cli.run(Array("--version")))
    assertEquals(0, Cli.run(Array("-v")))

  @Test
  def unrecognizedOptionReturnsOne(): Unit =
    assertEquals(1, Cli.run(Array("--bogus-nonexistent-flag")))

  @Test
  def wallpaperDryRunReturnsZeroWithoutStartingLoop(): Unit =
    assertEquals(0, Cli.run(Array("wallpaper", "--dry-run", "-q")))
