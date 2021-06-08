package io.ktor.utils.io.errors

import io.ktor.utils.io.core.*

public expect open class IOException(message: String, cause: Throwable?) : Exception {
    public constructor(message: String)
}

public expect open class EOFException(message: String) : IOException

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Not implemented.", level = DeprecationLevel.ERROR)
public fun <R> TODO_ERROR(value: R): Nothing = TODO("Not implemented. Value is $value")

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Not implemented.", level = DeprecationLevel.ERROR)
public fun TODO_ERROR(): Nothing = TODO("Not implemented.")

internal fun checkPeekTo(destination: Buffer, offset: Int, min: Int, max: Int) {
    require(offset >= 0) { "offset shouldn't be negative: $offset." }
    require(min >= 0) { "min shouldn't be negative: $min." }
    require(max >= min) { "max should't be less than min: max = $max, min = $min." }
    require(min <= destination.writeRemaining) {
        "Not enough free space in the destination buffer " +
            "to write the specified minimum number of bytes: min = $min, free = ${destination.writeRemaining}."
    }
}

@PublishedApi
internal fun incompatibleVersionError(): Nothing = throw Error(
    "This API is no longer supported. " +
        "Please downgrade kotlinx-io or recompile your project/dependencies with new kotlinx-io."
)
