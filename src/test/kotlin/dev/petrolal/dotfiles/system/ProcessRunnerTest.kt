package dev.petrolal.dotfiles.system

import dev.petrolal.dotfiles.domain.Result
import dev.petrolal.dotfiles.system.ProcessRunner.captureOutput
import dev.petrolal.dotfiles.system.ProcessRunner.run
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProcessRunnerTest {
    @Test
    fun captureOutputReturnsCleanTextOnSuccess() {
        val result = captureOutput(mutableListOf("echo", "hello world"))
        Assertions.assertTrue(result.isOk())
        Assertions.assertEquals("hello world\n", result.orElse(""))
    }

    @Test
    fun captureOutputReturnsErrorOnNonZeroExit() {
        val result = run(mutableListOf("sh", "-c", "exit 42"))
        Assertions.assertTrue(result.isErr())
    }

    @Test
    fun runReturnsOkOnSuccess() {
        val result = run(mutableListOf("true"))
        Assertions.assertTrue(result.isOk())
        Assertions.assertEquals(0, result.orElse(-1))
    }

    @Test
    fun runReturnsErrOnNonZeroExit() {
        val result = run(mutableListOf("false"))
        Assertions.assertTrue(result.isErr())
    }

    @Test
    fun runReturnsErrOnNonExistentBinary() {
        val result= run(mutableListOf("non_existent_binary_xyz_12345"))
        Assertions.assertTrue(result.isErr())
    }
}
