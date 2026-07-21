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
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readLineStrict
import kotlinx.io.writeString

/**
 * Creates storage that uses file system to store cache data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cache.storage.FileStorage)
 *
 * @param fileSystem file system to use for file operations.
 * @param directory directory to store cache data.
 * @param dispatcher dispatcher to use for file operations.
 */
@Suppress("FunctionName")
public fun FileStorage(
    fileSystem: FileSystem,
    directory: Path,
    dispatcher: CoroutineDispatcher = ioDispatcher()
): CacheStorage = CachingCacheStorage(FileCacheStorage(fileSystem, directory, dispatcher))

private class FileCacheStorage(
    private val fileSystem: FileSystem,
    private val directoryPath: Path,
    private val dispatcher: CoroutineDispatcher = ioDispatcher()
) : CacheStorage {

    private val mutexes = ConcurrentMap<String, Mutex>()

    init {
        fileSystem.createDirectories(directoryPath)
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

    private fun key(url: Url) = sha256(url.toString().encodeToByteArray()).toHexString()

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
            val path = Path(directoryPath, urlHex)
            if (!fileSystem.exists(path)) return@withLock

            try {
                fileSystem.delete(path)
            } catch (cause: Exception) {
                LOGGER.trace { "Exception during cache deletion in a file: ${cause.stackTraceToString()}" }
            }
        }
    }

    private suspend fun writeCacheUnsafe(urlHex: String, caches: List<CachedResponseData>) {
        try {
            withContext(dispatcher) {
                val path = Path(directoryPath, urlHex)
                fileSystem.sink(path).buffered().use { output ->
                    output.writeInt(caches.size)
                    for (cache in caches) {
                        writeCache(output, cache)
                    }
                }
            }
        } catch (cause: Exception) {
            if (cause is CancellationException) currentCoroutineContext().ensureActive()
            LOGGER.trace { "Exception during saving a cache to a file: ${cause.stackTraceToString()}" }
        }
    }

    private suspend fun readCacheUnsafe(urlHex: String): Set<CachedResponseData> {
        val path = Path(directoryPath, urlHex)
        if (!fileSystem.exists(path)) return emptySet()

        return try {
            withContext(dispatcher) {
                fileSystem.source(path).buffered().use { input ->
                    val requestsCount = input.readInt()
                    buildSet {
                        repeat(requestsCount) { add(readCache(input)) }
                    }
                }
            }
        } catch (cause: Exception) {
            LOGGER.trace { "Exception during cache lookup in a file: ${cause.stackTraceToString()}" }
            emptySet()
        }
    }

    private fun writeCache(sink: Sink, cache: CachedResponseData) {
        sink.writeString(cache.url.toString() + "\n")
        sink.writeInt(cache.statusCode.value)
        sink.writeString(cache.statusCode.description + "\n")
        sink.writeString(cache.version.toString() + "\n")
        val headers = cache.headers.flattenEntries()
        sink.writeInt(headers.size)
        for ((key, value) in headers) {
            sink.writeString(key + "\n")
            sink.writeString(value + "\n")
        }
        sink.writeLong(cache.requestTime.timestamp)
        sink.writeLong(cache.responseTime.timestamp)
        sink.writeLong(cache.expires.timestamp)
        sink.writeInt(cache.varyKeys.size)
        for ((key, value) in cache.varyKeys) {
            sink.writeString(key + "\n")
            sink.writeString(value + "\n")
        }
        sink.writeInt(cache.body.size)
        sink.writeFully(cache.body)
    }

    private fun readCache(source: Source): CachedResponseData {
        val url = source.readLineStrict()
        val status = HttpStatusCode(source.readInt(), source.readLineStrict())
        val version = HttpProtocolVersion.parse(source.readLineStrict())
        val headersCount = source.readInt()
        val headers = HeadersBuilder()
        repeat(headersCount) {
            val key = source.readLineStrict()
            val value = source.readLineStrict()
            headers.append(key, value)
        }
        val requestTime = GMTDate(source.readLong())
        val responseTime = GMTDate(source.readLong())
        val expirationTime = GMTDate(source.readLong())
        val varyKeysCount = source.readInt()
        val varyKeys = buildMap {
            repeat(varyKeysCount) {
                val key = source.readLineStrict().lowercase()
                val value = source.readLineStrict()
                put(key, value)
            }
        }
        val bodyCount = source.readInt()
        val body = ByteArray(bodyCount)
        source.readFully(body)
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
