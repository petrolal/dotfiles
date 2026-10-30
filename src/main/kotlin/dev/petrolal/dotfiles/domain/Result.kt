// Result.java --- Functional error handling
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.domain

import java.util.Objects

sealed interface Result<T> {
    @JvmRecord
    data class Ok<T>(val value: T) : Result<T>

    @JvmRecord
    data class Err<T>(val error: DotfileError) : Result<T>

    fun isOk(): Boolean = this is Ok<*>

    fun isErr(): Boolean = this is Err<*>

    fun getOrNull(): T? {
        return if (this is Ok<*>) {
            @Suppress("UNCHECKED_CAST")
            (this as Ok<T>).value
        } else {
            null
        }
    }

    fun orElse(fallback: T): T {
        return if (this is Ok<*>) {
            @Suppress("UNCHECKED_CAST")
            (this as Ok<T>).value
        } else {
            fallback
        }
    }

    companion object {
        @JvmStatic
        fun <T> ok(value: T): Result<T> {
            return Ok(value)
        }

        @JvmStatic
        fun <T> err(error: DotfileError): Result<T> {
            return Err(Objects.requireNonNull(error))
        }
    }
}

