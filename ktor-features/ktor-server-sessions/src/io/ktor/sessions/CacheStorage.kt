package io.ktor.sessions

import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*

class CacheStorage(val delegate: SessionStorage, val idleTimeout: Long) : SessionStorage {
    private val referenceCache = SoftReferenceCache<String, ByteArray> { id ->
        delegate.read(id) { input -> input.toInputStream().readBytes() }
    }
    private val cache = BaseTimeoutCache(idleTimeout, true, referenceCache)

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val readChannel = cache.getOrCompute(id)
        return consumer(ByteReadChannel(readChannel))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        cache.invalidate(id)
        return delegate.write(id, provider)
    }

    override suspend fun invalidate(id: String) {
        cache.invalidate(id)
        delegate.invalidate(id)
    }
}
