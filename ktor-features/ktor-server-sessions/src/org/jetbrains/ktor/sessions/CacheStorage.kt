package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.cio.*

class CacheStorage(val delegate: SessionStorage, val idleTimeout: Long) : SessionStorage {
    private val referenceCache = SoftReferenceCache<String, ByteArray> { id ->
        delegate.read(id) { input -> input.toInputStream().readBytes() }
    }
    private val cache = BaseTimeoutCache(idleTimeout, true, referenceCache)

    override suspend fun <R> read(id: String, consumer: suspend (ReadChannel) -> R): R {
        val readChannel = cache.getOrCompute(id).toReadChannel()
        return consumer(readChannel)
    }

    override suspend fun write(id: String, provider: suspend (WriteChannel) -> Unit) {
        cache.invalidate(id)
        return delegate.write(id, provider)
    }

    override suspend fun invalidate(id: String) {
        cache.invalidate(id)
        delegate.invalidate(id)
    }
}
