package io.ktor.sessions

import kotlinx.coroutines.experimental.io.*

/**
 * Represents a way to [write], [read] and [invalidate] session bits.
 */
interface SessionStorage {
    /**
     * Writes a session [id] using a specific [provider].
     *
     * This method will call the [provider] with a [ByteWriteChannel] while being in charge of its lifecycle.
     * [provider] is in charge of writing session bits to the specified [ByteWriteChannel].
     */
    suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit)

    /**
     * Invalidates session [id].
     *
     * This method will prevent that session [id] from being accessible after this call.
     *
     * @throws NoSuchElementException when session [id] is not found.
     */
    suspend fun invalidate(id: String)

    /**
     * Reads session [id] using a [consumer] as [R]
     *
     * This method will call the [consumer] with a [ByteReadChannel] while being in charge of its lifecycle,
     * and will return the object [R] produced by the [consumer].
     * [consumer] should read the content of the specified [ByteReadChannel] and return an object of type [R].
     *
     * @return instance of [R] representing the session object returned from the [consumer].
     * @throws NoSuchElementException when session [id] is not found.
     */
    suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R
}
