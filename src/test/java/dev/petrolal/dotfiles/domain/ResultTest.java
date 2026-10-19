package dev.petrolal.dotfiles.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    void okIsOkAndNotErr() {
        Result<String> ok = Result.ok("value");
        assertTrue(ok.isOk());
        assertFalse(ok.isErr());
        assertEquals("value", ok.orElse("fallback"));
    }

    @Test
    void errIsErrAndNotOk() {
        DotfileError error = new DotfileError.IoError("boom");
        Result<String> err = Result.err(error);
        assertFalse(err.isOk());
        assertTrue(err.isErr());
        assertEquals("fallback", err.orElse("fallback"));
    }

    @Test
    void sideEffectHooksWork() {
        AtomicBoolean okCalled = new AtomicBoolean(false);
        AtomicBoolean errCalled = new AtomicBoolean(false);

        Result.ok("hello")
                .ifOk(val -> okCalled.set(true))
                .ifErr(err -> errCalled.set(true));

        assertTrue(okCalled.get());
        assertFalse(errCalled.get());

        okCalled.set(false);
        Result.err(new DotfileError.IoError("fail"))
                .ifOk(val -> okCalled.set(true))
                .ifErr(err -> errCalled.set(true));

        assertFalse(okCalled.get());
        assertTrue(errCalled.get());
    }
}
