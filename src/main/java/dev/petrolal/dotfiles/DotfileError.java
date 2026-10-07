// DotfileError.java --- Typed domain errors for Result<T>
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles;


import java.nio.file.Path;

sealed interface DotfileError
        permits DotfileError.CommandFailed, DotfileError.FileNotFound,
        DotfileError.IoError, DotfileError.ParseError {
    record CommandFailed(String command, int exitCode) implements DotfileError {}
    record FileNotFound(Path path) implements DotfileError {}
    record IoError(String message) implements DotfileError {}
    record ParseError(String message) implements DotfileError {}

    default String describe() {
        return switch (this) {
            case CommandFailed c -> "command failed (" + c.exitCode() + "): " + c.command();
            case FileNotFound f -> "file not found: " + f.path();
            case IoError i -> "I/O error: " + i.message();
            case ParseError p -> "parse error: " + p.message();
        };
    }
}
