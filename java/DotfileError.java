// DotfileError.java --- Typed domain errors for Result<T>
// License: GPL-3.0-or-later

import java.nio.file.Path;

sealed interface DotfileError
        permits DotfileError.CommandFailed, DotfileError.FileNotFound,
        DotfileError.IoError, DotfileError.ParseError {
    record CommandFailed(String command, int exitCode) implements DotfileError {}
    record FileNotFound(Path path) implements DotfileError {}
    record IoError(String message) implements DotfileError {}
    record ParseError(String message) implements DotfileError {}

    default String describe() {
        if (this instanceof CommandFailed c) return "command failed (" + c.exitCode() + "): " + c.command();
        if (this instanceof FileNotFound f) return "file not found: " + f.path();
        if (this instanceof IoError i) return "I/O error: " + i.message();
        if (this instanceof ParseError p) return "parse error: " + p.message();
        throw new IllegalStateException("unreachable");
    }
}
