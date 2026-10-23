package dev.petrolal.dotfiles.system

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProcessRunnerTest:

  @Test
  def captureOutputReturnsCleanTextOnSuccess(): Unit =
    val result = ProcessRunner.captureOutput(List("echo", "hello world"))
    assertTrue(result.isOk)
    assertEquals("hello world\n", result.orElse(""))

  @Test
  def captureOutputReturnsErrorOnNonZeroExit(): Unit =
    val result = ProcessRunner.captureOutput(List("sh", "-c", "exit 42"))
    assertTrue(result.isErr)

  @Test
  def runReturnsOkOnSuccess(): Unit =
    val result = ProcessRunner.run(List("true"))
    assertTrue(result.isOk)
    assertEquals(0, result.orElse(-1))

  @Test
  def runReturnsErrOnNonZeroExit(): Unit =
    val result = ProcessRunner.run(List("false"))
    assertTrue(result.isErr)

  @Test
  def runReturnsErrOnNonExistentBinary(): Unit =
    val result = ProcessRunner.run(List("non_existent_binary_xyz_12345"))
    assertTrue(result.isErr)
