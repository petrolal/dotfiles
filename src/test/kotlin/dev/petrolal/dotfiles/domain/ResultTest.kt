package dev.petrolal.dotfiles.domain

import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result.Companion.err
import dev.petrolal.dotfiles.domain.Result.Companion.ok
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

internal class ResultTest {
    @Test
    fun okIsOkAndNotErr() {
        val ok = ok<String?>("value")
        Assertions.assertTrue(ok.isOk())
        Assertions.assertFalse(ok.isErr())
        Assertions.assertEquals("value", ok.orElse("fallback"))
    }

    @Test
    fun errIsErrAndNotOk() {
        val error: DotfileError = IoError("boom")
        val err = err<String?>(error)
        Assertions.assertFalse(err.isOk())
        Assertions.assertTrue(err.isErr())
        Assertions.assertEquals("fallback", err.orElse("fallback"))
    }

    @Test
    fun sideEffectHooksWork() {
        val okCalled = AtomicBoolean(false)
        val errCalled = AtomicBoolean(false)

        ok<String?>("hello")
            .ifOk(Consumer { `val`: String? -> okCalled.set(true) })
            .ifErr(Consumer { err: DotfileError? -> errCalled.set(true) })

        Assertions.assertTrue(okCalled.get())
        Assertions.assertFalse(errCalled.get())

        okCalled.set(false)
        err<Any?>(IoError("fail"))
            .ifOk(Consumer { `val`: Any? -> okCalled.set(true) })
            .ifErr(Consumer { err: DotfileError? -> errCalled.set(true) })

        Assertions.assertFalse(okCalled.get())
        Assertions.assertTrue(errCalled.get())
    }
}
