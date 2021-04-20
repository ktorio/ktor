package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.errors.checkPeekTo

/**
 * Copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
 * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes then
 * it simply return number of available bytes with no exception so the returned value need
 * to be checked.
 * It is safe to specify `max > destination.writeRemaining` but
 * `min` shouldn't be bigger than the [destination] free space.
 * This function could trigger the underlying source reading that may lead to blocking I/O.
 * It is safe to specify too big [offset] so in this case this function will always return `0`.
 * This function usually copy more bytes than [min] (unless `max = min`) but it is not guaranteed.
 * When `0` is returned with `offset = 0` then it makes sense to check [Input.endOfInput].
 *
 * @param destination to write bytes
 * @param offset to skip input
 * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
 * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
 * @return number of bytes copied to the [destination] possibly `0`
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
public fun Input.peekTo(destination: ChunkBuffer, offset: Int = 0, min: Int = 1, max: Int = Int.MAX_VALUE): Int {
    return peekTo(destination as Buffer, offset, min, max)
}

/**
 * Copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
 * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes then
 * it simply return number of available bytes with no exception so the returned value need
 * to be checked.
 * It is safe to specify `max > destination.writeRemaining` but
 * `min` shouldn't be bigger than the [destination] free space.
 * This function could trigger the underlying source reading that may lead to blocking I/O.
 * It is safe to specify too big [offset] so in this case this function will always return `0`.
 * This function usually copy more bytes than [min] (unless `max = min`) but it is not guaranteed.
 * When `0` is returned with `offset = 0` then it makes sense to check [Input.endOfInput].
 *
 * @param destination to write bytes
 * @param offset to skip input
 * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
 * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
 * @return number of bytes copied to the [destination] possibly `0`
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "DEPRECATION")
@Deprecated("Use peekTo(Memory) instead.")
public fun Input.peekTo(destination: Buffer, offset: Int = 0, min: Int = 1, max: Int = Int.MAX_VALUE): Int {
    checkPeekTo(destination, offset, min, max)

    val copied = peekTo(
        destination.memory,
        destination.writePosition.toLong(),
        offset.toLong(),
        min.toLong(),
        max.coerceAtMost(destination.writeRemaining).toLong()
    ).toInt()

    destination.commitWritten(copied)
    return copied
}
