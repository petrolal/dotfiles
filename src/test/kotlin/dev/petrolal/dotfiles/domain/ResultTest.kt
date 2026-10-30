package dev.petrolal.dotfiles.domain

import dev.petrolal.dotfiles.domain.DotfileError.IoError
import dev.petrolal.dotfiles.domain.Result.Companion.err
import dev.petrolal.dotfiles.domain.Result.Companion.ok
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
}
