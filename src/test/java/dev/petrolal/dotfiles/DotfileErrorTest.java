package dev.petrolal.dotfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DotfileErrorTest {

    @Test
    void describeCoversEveryVariant() {
        assertEquals("command failed (1): xfconf-query",
                new DotfileError.CommandFailed("xfconf-query", 1).describe());
        assertEquals("file not found: /tmp/missing",
                new DotfileError.FileNotFound(Path.of("/tmp/missing")).describe());
        assertEquals("I/O error: disk full",
                new DotfileError.IoError("disk full").describe());
        assertEquals("parse error: unexpected token",
                new DotfileError.ParseError("unexpected token").describe());
    }
}
