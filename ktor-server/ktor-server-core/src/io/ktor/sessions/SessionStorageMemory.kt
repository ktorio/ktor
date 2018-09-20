package io.ktor.sessions

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import java.util.concurrent.*

/**
 * [SessionStorage] that stores session contents into memory.
 *
 * Since it doesn't use any TTL sessions, memory usage will increase while the application is running
 * and session information will be discarded once the server stops.
 *
 * This is intended for development.
 */
class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentHashMap<String, ByteArray>()

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        coroutineScope {
            val channel = writer(Dispatchers.Unconfined, autoFlush = true) {
                provider(channel)
            }.channel

            sessions[id] = channel.toByteArray()
        }
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R =
            sessions[id]?.let { data -> consumer(ByteReadChannel(data)) }
                    ?: throw NoSuchElementException("Session $id not found")

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
