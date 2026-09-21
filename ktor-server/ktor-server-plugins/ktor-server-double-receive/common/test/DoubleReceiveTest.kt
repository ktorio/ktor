/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DoubleReceiveTest {

    @Test
    fun testInMemoryCache() = runTest {
        val content = ByteArray(1024 * 1024) { it.toByte() }
        val cache = MemoryCache(
            ByteReadChannel(content),
            EmptyCoroutineContext
        )

        repeat(3) {
            val received = cache.read().readBuffer().readByteArray()
            assertContentEquals(content, received)
        }
    }

    @Test
    fun testOverlappingInMemoryCacheReadersAreRejected() = runTest {
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext)
        val first = cache.read()
        val content = ByteArray(1024 * 1024) { it.toByte() }

        assertFailsWith<RequestAlreadyConsumedException> { cache.read() }
        body.writeFully(content)
        body.close()
        assertContentEquals(content, first.readBuffer().readByteArray())

        val second = cache.read()
        assertFailsWith<RequestAlreadyConsumedException> { cache.read() }
        second.cancel()

        assertContentEquals(content, cache.read().readBuffer().readByteArray())
    }

    @Test
    fun testFirstInMemoryCacheReaderStreamsBeforeBodyIsComplete() = runTest {
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext)
        val first = cache.read()

        body.writeByte(42)
        body.flush()

        assertEquals(42, withTimeout(1.seconds) { first.readByte() })
        assertFalse(body.isClosedForRead)
        body.close()
        first.discard()
    }

    @Test
    fun testFirstInMemoryCacheReaderCancellationIsPropagated() = runTest {
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext)
        val first = cache.read()
        val failure = IllegalStateException("First reader failed")

        first.cancel(failure)

        withTimeout(1.seconds) {
            assertTrue(assertFails { cache.read() }.isCausedBy(failure))
        }
        assertTrue(body.closedCause.isCausedBy(failure))
    }

    @Test
    fun testInMemoryCacheFailureIsPropagatedToAllReaders() = runTest {
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext)
        val first = cache.read()
        val failure = IllegalStateException("Body failed")

        body.close(failure)

        assertTrue(assertFails { first.readBuffer() }.isCausedBy(failure))
        assertTrue(assertFails { cache.read() }.isCausedBy(failure))
    }

    @Test
    fun testInMemoryCacheDisposalCancelsWaitingReaders() = runTest {
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext)
        val reader = cache.read()

        cache.dispose()

        withTimeout(1.seconds) {
            assertFailsWith<kotlinx.coroutines.CancellationException> { reader.readBuffer() }
            assertFailsWith<kotlinx.coroutines.CancellationException> { cache.read() }
        }
        body.close()
    }

    @Test
    fun testInMemoryCacheCallCancellationIsPropagatedToAllReaders() = runTest {
        val callJob = Job()
        val body = ByteChannel()
        val cache = MemoryCache(body, coroutineContext + callJob)
        val first = cache.read()

        callJob.cancel()

        withTimeout(1.seconds) {
            assertFailsWith<kotlinx.coroutines.CancellationException> { first.readBuffer() }
            assertFailsWith<kotlinx.coroutines.CancellationException> { cache.read() }
        }
        body.close()
    }
}

private fun Throwable?.isCausedBy(expected: Throwable): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current === expected) return true
        current = current.cause
    }
    return false
}
