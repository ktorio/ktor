/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

@Suppress("KDocMissingDocumentation")
public class CacheStorage(public val delegate: SessionStorage, idleTimeout: Long) : SessionStorage {
    private val referenceCache = SoftReferenceCache<String, ByteArray> { id ->
        delegate.read(id) { input -> input.readRemaining().readBytes() }
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
