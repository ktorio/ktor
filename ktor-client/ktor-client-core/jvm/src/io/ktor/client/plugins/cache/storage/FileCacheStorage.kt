/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cache.storage

import io.ktor.client.plugins.cache.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/**
 * Creates storage that uses file system to store cache data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cache.storage.FileStorage)
 *
 * @param directory directory to store cache data.
 * @param dispatcher dispatcher to use for file operations.
 */
@Suppress("FunctionName")
public fun FileStorage(
    directory: File,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): CacheStorage = CachingCacheStorage(FileCacheStorage(directory, dispatcher))

internal class CachingCacheStorage(
    private val delegate: CacheStorage
) : CacheStorage {

    private val store = ConcurrentMap<Url, Set<CachedResponseData>>()

    override suspend fun store(url: Url, data: CachedResponseData) {
        delegate.store(url, data)
        store[url] = delegate.findAll(url)
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        val data = store.getValue(url)
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        return store.getValue(url)
    }

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) {
        delegate.remove(url, varyKeys)
        store[url] = delegate.findAll(url)
    }

    override suspend fun removeAll(url: Url) {
        delegate.removeAll(url)
        store.remove(url)
    }
}

private class FileCacheStorage(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CacheStorage {

    private val mutexes = ConcurrentMap<String, Mutex>()

    init {
        directory.mkdirs()
    }

    override suspend fun store(url: Url, data: CachedResponseData): Unit = withContext(dispatcher) {
        val urlHex = key(url)
        updateCache(urlHex) { caches ->
            caches.filterNot { it.varyKeys == data.varyKeys } + data
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> = withContext(dispatcher) {
        readCache(key(url)).toSet()
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? = withContext(dispatcher) {
        val data = readCache(key(url))
        data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) = withContext(dispatcher) {
        val urlHex = key(url)
        updateCache(urlHex) { caches ->
            caches.filterNot { it.varyKeys == varyKeys }
        }
    }

    override suspend fun removeAll(url: Url) = withContext(dispatcher) {
        val urlHex = key(url)
        deleteCache(urlHex)
    }

    private fun key(url: Url) = hex(MessageDigest.getInstance("SHA-256").digest(url.toString().encodeToByteArray()))

    private suspend fun readCache(urlHex: String): Set<CachedResponseData> {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        return mutex.withLock { readCacheUnsafe(urlHex) }
    }

    private suspend inline fun updateCache(
        urlHex: String,
        transform: (Set<CachedResponseData>) -> List<CachedResponseData>
    ) {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val caches = readCacheUnsafe(urlHex)
            writeCacheUnsafe(urlHex, transform(caches))
        }
    }

    private suspend fun deleteCache(urlHex: String) {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val file = File(directory, urlHex)
            if (!file.exists()) return@withLock

            try {
                file.delete()
            } catch (cause: Exception) {
                LOGGER.trace { "Exception during cache deletion in a file: ${cause.stackTraceToString()}" }
            }
        }
    }

    private suspend fun writeCacheUnsafe(urlHex: String, caches: List<CachedResponseData>) {
        val channel = ByteChannel()
        try {
            coroutineScope {
                File(directory, urlHex).outputStream().buffered().use { output ->
                    launch {
                        channel.writeInt(caches.size)
                        for (cache in caches) {
                            writeCache(channel, cache)
                        }
                        channel.close()
                    }
                    channel.copyTo(output)
                }
            }
        } catch (cause: Exception) {
            if (cause is CancellationException) currentCoroutineContext().ensureActive()
            LOGGER.trace { "Exception during saving a cache to a file: ${cause.stackTraceToString()}" }
        } finally {
            channel.close()
        }
    }

    private suspend fun readCacheUnsafe(urlHex: String): Set<CachedResponseData> {
        val file = File(directory, urlHex)
        if (!file.exists()) return emptySet()

        try {
            file.inputStream().buffered().use {
                val channel = it.toByteReadChannel()
                val requestsCount = channel.readInt()
                val caches = mutableSetOf<CachedResponseData>()
                for (i in 0 until requestsCount) {
                    caches.add(readCache(channel))
                }
                channel.discard()
                return caches
            }
        } catch (cause: Exception) {
            LOGGER.trace { "Exception during cache lookup in a file: ${cause.stackTraceToString()}" }
            return emptySet()
        }
    }

    private suspend fun writeCache(channel: ByteChannel, cache: CachedResponseData) {
        channel.writeStringUtf8(cache.url.toString() + "\n")
        channel.writeInt(cache.statusCode.value)
        channel.writeStringUtf8(cache.statusCode.description + "\n")
        channel.writeStringUtf8(cache.version.toString() + "\n")
        val headers = cache.headers.flattenEntries()
        channel.writeInt(headers.size)
        for ((key, value) in headers) {
            channel.writeStringUtf8(key + "\n")
            channel.writeStringUtf8(value + "\n")
        }
        channel.writeLong(cache.requestTime.timestamp)
        channel.writeLong(cache.responseTime.timestamp)
        channel.writeLong(cache.expires.timestamp)
        channel.writeInt(cache.varyKeys.size)
        for ((key, value) in cache.varyKeys) {
            channel.writeStringUtf8(key + "\n")
            channel.writeStringUtf8(value + "\n")
        }
        channel.writeInt(cache.body.size)
        channel.writeFully(cache.body)
    }

    /**
     * Deserialize a single [CachedResponseData] from the provided [ByteReadChannel].
     *
     * Reads the cached-entry fields in the stored binary format: request URL, HTTP status and version,
     * headers, request/response/expiration timestamps, vary keys (converted to lowercase), and the body bytes.
     *
     * @param channel Source channel positioned at the start of a serialized cache entry.
     * @return The reconstructed [CachedResponseData] instance.
     */
    private suspend fun readCache(channel: ByteReadChannel): CachedResponseData {
        val url = channel.readLineStrict()!!
        val status = HttpStatusCode(channel.readInt(), channel.readLineStrict()!!)
        val version = HttpProtocolVersion.parse(channel.readLineStrict()!!)
        val headersCount = channel.readInt()
        val headers = HeadersBuilder()
        repeat(headersCount) {
            val key = channel.readLineStrict()!!
            val value = channel.readLineStrict()!!
            headers.append(key, value)
        }
        val requestTime = GMTDate(channel.readLong())
        val responseTime = GMTDate(channel.readLong())
        val expirationTime = GMTDate(channel.readLong())
        val varyKeysCount = channel.readInt()
        val varyKeys = buildMap {
            repeat(varyKeysCount) {
                val key = channel.readLineStrict()!!.lowercase()
                val value = channel.readLineStrict()!!
                put(key, value)
            }
        }
        val bodyCount = channel.readInt()
        val body = ByteArray(bodyCount)
        channel.readFully(body)
        return CachedResponseData(
            url = Url(url),
            statusCode = status,
            requestTime = requestTime,
            responseTime = responseTime,
            version = version,
            expires = expirationTime,
            headers = headers.build(),
            varyKeys = varyKeys,
            body = body
        )
    }
}
