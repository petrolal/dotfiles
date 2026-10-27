package dev.petrolal.dotfiles.domain

import dev.petrolal.dotfiles.domain.DotfileError.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class DotfileErrorTest {
    @Test
    fun describeCoversEveryVariant() {
        Assertions.assertEquals(
            "command failed (1): xfconf-query",
            CommandFailed("xfconf-query", 1).describe()
        )
        Assertions.assertEquals(
            "file not found: /tmp/missing",
            FileNotFound(Path.of("/tmp/missing")).describe()
        )
        Assertions.assertEquals(
            "I/O error: disk full",
            IoError("disk full").describe()
        )
        Assertions.assertEquals(
            "parse error: unexpected token",
            ParseError("unexpected token").describe()
        )
    }
}
