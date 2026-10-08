// Result.java --- Functional error handling
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.domain;

public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(DotfileError error) implements Result<T> {}

    default boolean isOk() {
        return this instanceof Ok<T>;
    }
}
