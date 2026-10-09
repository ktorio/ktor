/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DoubleReceiveTestJvm {

    @Test
    fun testFileCache() = runBlocking {
        val content = ByteArray(1024 * 1024) { it.toByte() }
        val cache = startFileCache(ByteReadChannel(content))

        repeat(3) {
            val received = cache.reader().readBuffer().readByteArray()
            assertContentEquals(content, received)
        }
        cache.dispose()
    }

    @Test
    fun testFileCacheReaderWaitsForBody() = runBlocking {
        val body = ByteChannel()
        val cache = startFileCache(body)
        val reader = async { cache.reader() }
        val content = ByteArray(4 * 1024 * 1024) { it.toByte() }

        withTimeout(5.seconds) {
            body.writeFully(content)
            body.close()
            assertContentEquals(content, reader.await().readBuffer().readByteArray())
        }
        cache.dispose()
    }

    @Test
    fun testFileCacheDisposalFailsWaitingReader() = runBlocking<Unit> {
        val body = ByteChannel()
        val cache = startFileCache(body)
        val reader = async { runCatching { cache.reader() } }
        yield()

        cache.dispose()

        withTimeout(5.seconds) {
            assertFailsWith<IOException> { reader.await().getOrThrow() }
        }
        body.close()
    }

    @Test
    fun testFileCacheReadAfterDisposeFails() = runBlocking<Unit> {
        val body = ByteChannel()
        val cache = FileCache()
        val pump = with(cache) { launchPump(channel = body) }

        cache.dispose()

        val content = ByteArray(4 * 1024 * 1024)
        withTimeout(5.seconds) {
            body.writeFully(content)
            body.close()
            pump.join()
        }
        assertFalse(body.isClosedForRead)
        assertNull(body.closedCause)
        assertFailsWith<IOException>("File cache disposed") { cache.reader() }
    }
}

// Starts the cache the same way the plugin does.
private fun CoroutineScope.startFileCache(body: ByteReadChannel): FileCache =
    FileCache().apply { launchPump(channel = body) }
