package dev.petrolal.dotfiles.cli

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class SpringShellTest {
    @Test
    fun springShellVersionCommand() {
        val exitCode = ImpApplication.run(arrayOf<String>("version"))
        Assertions.assertEquals(0, exitCode)
    }

    @Test
    fun springShellHelpCommand() {
        val exitCode = ImpApplication.run(arrayOf<String>("help"))
        Assertions.assertEquals(0, exitCode)
    }

    @Test
    fun springShellDeployDryRun() {
        val exitCode = ImpApplication.run(arrayOf<String>("deploy", "--dry-run"))
        Assertions.assertEquals(0, exitCode)
    }
}
