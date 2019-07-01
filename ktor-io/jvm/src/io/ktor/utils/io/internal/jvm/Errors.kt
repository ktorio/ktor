package io.ktor.utils.io.internal.jvm

@PublishedApi
internal fun negativeShiftError(delta: Int): Nothing =
    throw IllegalStateException("Wrong buffer position change: negative shift $delta")

@PublishedApi
internal fun limitChangeError(): Nothing = throw IllegalStateException("Limit change is now allowed")

@PublishedApi
internal fun wrongBufferPositionChangeError(delta: Int, size: Int): Nothing =
    throw IllegalStateException("Wrong buffer position change: $delta. " +
            "Position should be moved forward only by at most size bytes (size = $size)")
