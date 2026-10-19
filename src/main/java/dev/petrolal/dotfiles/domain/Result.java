// Result.java --- Functional error handling
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.domain;

import java.util.Objects;
import java.util.function.Consumer;

public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(DotfileError error) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(DotfileError error) {
        return new Err<>(Objects.requireNonNull(error));
    }

    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    default boolean isErr() {
        return this instanceof Err<T>;
    }

    default T orElse(T fallback) {
        if (this instanceof Ok<T> ok) {
            return ok.value();
        }
        return fallback;
    }

    default Result<T> ifOk(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (this instanceof Ok<T> ok) {
            action.accept(ok.value());
        }
        return this;
    }

    default Result<T> ifErr(Consumer<? super DotfileError> action) {
        Objects.requireNonNull(action);
        if (this instanceof Err<T> err) {
            action.accept(err.error());
        }
        return this;
    }
}
