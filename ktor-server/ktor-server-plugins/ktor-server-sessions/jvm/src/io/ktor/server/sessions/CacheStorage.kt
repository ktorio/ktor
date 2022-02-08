/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

@Suppress("KDocMissingDocumentation")
public class CacheStorage(public val delegate: SessionStorage, idleTimeout: Long) : SessionStorage {
    private val referenceCache = SoftReferenceCache<String, String> { id -> delegate.read(id) }
    private val cache = BaseTimeoutCache(idleTimeout, true, referenceCache)

    override suspend fun read(id: String): String {
        return cache.getOrCompute(id)
    }

    override suspend fun write(id: String, value: String) {
        val cachedValue = try {
            read(id)
        } catch (_: Throwable) {
            null
        }
        if (cachedValue == value) return
        cache.invalidate(id)
        delegate.write(id, value)
    }

    override suspend fun invalidate(id: String) {
        cache.invalidate(id)
        delegate.invalidate(id)
    }
}
