/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.test.*
import io.ktor.utils.io.*
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DoubleReceiveTest {

    @Test
    fun testInMemoryCache() = runTest {
        val content = ByteArray(1024 * 1024) { it.toByte() }
        val cache = startMemoryCache(ByteReadChannel(content))

        repeat(3) {
            val received = cache.reader().readBuffer().readByteArray()
            assertContentEquals(content, received)
        }
    }

    @Test
    fun testInMemoryCacheDetachesOldReader() = runTest {
        val body = ByteChannel()
        val cache = startMemoryCache(body)
        val first = cache.reader()
        // Larger than the channel buffer, so the pump is blocked on the abandoned first reader
        val content = ByteArray(4 * 1024 * 1024) { it.toByte() }
        val writer = launch {
            body.writeFully(content)
            body.close()
        }

        first.readByteArray(1024)
        val second = withTimeout(1.seconds) { cache.reader() }

        assertFailsWith<IOException> { first.readBuffer() }
        assertContentEquals(content, second.readBuffer().readByteArray())
        writer.join()
    }

    @Test
    fun testFirstInMemoryCacheReaderStreamsBeforeBodyIsComplete() = runTest {
        val body = ByteChannel()
        val cache = startMemoryCache(body)
        val first = cache.reader()

        body.writeByte(42)
        body.flush()

        assertEquals(42, withTimeout(1.seconds) { first.readByte() })
        assertFalse(body.isClosedForRead)
        body.close()
        first.discard()
    }

    @Test
    fun testInMemoryCacheReaderCancellationIsNotPropagated() = runTest {
        val body = ByteChannel()
        val cache = startMemoryCache(body)
        val content = ByteArray(10) { it.toByte() }
        body.writeFully(content)
        body.close()

        val first = cache.reader()
        first.cancel(cause = IllegalStateException("Reader failed"))

        assertTrue(first.isClosedForRead)
        assertFalse(body.isClosedForRead)

        val secondContent = cache.reader().readBuffer().readByteArray()
        assertContentEquals(content, secondContent)
    }

    @Test
    fun testInMemoryCacheFailureIsPropagatedToAllReaders() = runTest {
        val body = ByteChannel()
        val cache = startMemoryCache(body)
        val first = cache.reader()
        val failure = IllegalStateException("Body failed")

        body.close(failure)

        assertTrue(assertFails { first.readBuffer() }.isCausedBy(failure))
        assertTrue(assertFails { cache.reader() }.isCausedBy(failure))
    }

    @Test
    fun testInMemoryCacheDisposalFailsReaders() = runTest {
        val body = ByteChannel()
        val cache = InMemoryCache()
        val pump = with(cache) { launchPump(channel = body) }
        val reader = cache.reader()

        cache.dispose()

        withTimeout(1.seconds) {
            assertFailsWith<IOException> { reader.readBuffer() }
            assertFailsWith<IOException> { cache.reader() }

            // Larger than the channel buffers, so it's written only if the cache keeps reading the body
            body.writeFully(ByteArray(4 * 1024 * 1024))
            body.close()
            pump.join()
        }
        assertFalse(body.isClosedForRead)
        assertNull(body.closedCause)
    }

    @Test
    fun testInMemoryCacheCallCancellationIsPropagatedToAllReaders() = runTest {
        val callJob = Job()
        val body = ByteChannel()
        val cache = CoroutineScope(coroutineContext + callJob).startMemoryCache(body)
        val first = cache.reader()

        callJob.cancel()

        withTimeout(1.seconds) {
            assertFailsWith<IOException> { first.readBuffer() }
            assertFailsWith<IOException> { cache.reader() }
        }
        body.close()
    }
}

private fun CoroutineScope.startMemoryCache(body: ByteReadChannel): InMemoryCache =
    InMemoryCache().apply { launchPump(channel = body) }

private fun Throwable?.isCausedBy(expected: Throwable): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current === expected) return true
        current = current.cause
    }
    return false
}
