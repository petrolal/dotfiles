package dev.petrolal.dotfiles.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessRunnerTest {

    @Test
    void captureOutputReturnsCleanTextOnSuccess() {
        var result = ProcessRunner.captureOutput(List.of("echo", "hello world"));
        assertTrue(result.isOk());
        assertEquals("hello world\n", result.orElse(""));
    }

    @Test
    void captureOutputReturnsErrorOnNonZeroExit() {
        var result = ProcessRunner.captureOutput(List.of("sh", "-c", "exit 42"));
        assertTrue(result.isErr());
    }

    @Test
    void runReturnsOkOnSuccess() {
        var result = ProcessRunner.run(List.of("true"));
        assertTrue(result.isOk());
        assertEquals(0, result.orElse(-1));
    }

    @Test
    void runReturnsErrOnNonZeroExit() {
        var result = ProcessRunner.run(List.of("false"));
        assertTrue(result.isErr());
    }

    @Test
    void runReturnsErrOnNonExistentBinary() {
        var result = ProcessRunner.run(List.of("non_existent_binary_xyz_12345"));
        assertTrue(result.isErr());
    }
}
