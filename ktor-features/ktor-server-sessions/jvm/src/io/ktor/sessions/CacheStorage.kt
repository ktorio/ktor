/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
class CacheStorage(val delegate: SessionStorage, idleTimeout: Long) : SessionStorage {
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
