package dev.petrolal.dotfiles.cli

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class CliTest {
    @Test
    fun helpReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("--help")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("-h")))
    }

    @Test
    fun versionReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("--version")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("-v")))
    }

    @Test
    fun unrecognizedOptionReturnsOne() {
        Assertions.assertEquals(1, Cli.run(arrayOf<String>("--bogus-nonexistent-flag")))
    }

    @Test
    fun overlayDryRunReturnsZeroWithoutOpeningWindow() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("overlay", "--dry-run", "-q")))
    }
}
