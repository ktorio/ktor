package io.ktor.sessions

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*

class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentHashMap<String, ByteArray>()

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        sessions[id] = writer(Unconfined, autoFlush = true) {
            provider(channel)
        }.channel.toByteArray()
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R =
            sessions[id]?.let { data -> consumer(ByteReadChannel(data)) }
                    ?: throw NoSuchElementException("Session $id not found")

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
