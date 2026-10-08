package dev.petrolal.dotfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.petrolal.dotfiles.domain.DotfileError;
import dev.petrolal.dotfiles.domain.Result;

class ResultTest {

    @Test
    void okIsOk() {
        Result<String> ok = new Result.Ok<>("value");
        assertTrue(ok.isOk());
    }

    @Test
    void errIsNotOk() {
        Result<String> err = new Result.Err<>(new DotfileError.IoError("boom"));
        assertFalse(err.isOk());
    }
}
