package dev.petrolal.dotfiles.cli

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class CliTest {
    @Test
    fun helpReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("--help")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("-h")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("help")))
    }

    @Test
    fun versionReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("--version")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("-v")))
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("version")))
    }

    @Test
    fun unrecognizedOptionReturnsOne() {
        Assertions.assertEquals(1, Cli.run(arrayOf<String>("--bogus-nonexistent-flag")))
        Assertions.assertEquals(1, Cli.run(arrayOf<String>("bogus-command")))
    }

    @Test
    fun deployDryRunReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("deploy", "--dry-run", "-q")))
    }

    @Test
    fun uninstallDryRunReturnsZero() {
        Assertions.assertEquals(0, Cli.run(arrayOf<String>("uninstall", "--dry-run", "-q")))
    }
}
