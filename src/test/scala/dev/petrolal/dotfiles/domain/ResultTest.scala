package dev.petrolal.dotfiles.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class ResultTest:

  @Test
  def okIsOkAndNotErr(): Unit =
    val ok = Result.ok("value")
    assertTrue(ok.isOk)
    assertFalse(ok.isErr)
    assertEquals("value", ok.orElse("fallback"))

  @Test
  def errIsErrAndNotOk(): Unit =
    val error = DotfileError.IoError("boom")
    val err = Result.err[String](error)
    assertFalse(err.isOk)
    assertTrue(err.isErr)
    assertEquals("fallback", err.orElse("fallback"))

  @Test
  def sideEffectHooksWork(): Unit =
    val okCalled = AtomicBoolean(false)
    val errCalled = AtomicBoolean(false)

    Result
      .ok("hello")
      .ifOk(_ => okCalled.set(true))
      .ifErr(_ => errCalled.set(true))

    assertTrue(okCalled.get())
    assertFalse(errCalled.get())

    okCalled.set(false)
    Result
      .err[String](DotfileError.IoError("fail"))
      .ifOk(_ => okCalled.set(true))
      .ifErr(_ => errCalled.set(true))

    assertFalse(okCalled.get())
    assertTrue(errCalled.get())
