/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawSourceChannelTest {

    @Test
    fun `awaitContent returns false when channel is closed with empty buffer`() = runTest {
        val channel = RawSourceChannel(sourceOf(ByteArray(0)), EmptyCoroutineContext)

        // First call closes the channel internally (EOF)
        val firstResult = channel.awaitContent(1)
        assertFalse(firstResult, "awaitContent should return false on EOF")

        // Second call hits the closedToken != null path
        val secondResult = channel.awaitContent(1)
        assertFalse(secondResult, "awaitContent should return false when channel is already closed with empty buffer")
    }

    @Test
    fun `awaitContent returns true when channel is closed with data in buffer`() = runTest {
        val channel = RawSourceChannel(sourceOf(ByteArray(10) { it.toByte() }), EmptyCoroutineContext)

        // Request more than available to trigger EOF and close the channel
        val firstResult = channel.awaitContent(20)
        assertFalse(firstResult, "awaitContent should return false when not enough data")

        // Channel is now closed, but 10 bytes remain in the buffer
        val secondResult = channel.awaitContent(1)
        assertTrue(secondResult, "awaitContent should return true when closed channel still has data in buffer")
    }

    @Test
    fun `awaitContent throws when channel is cancelled`() = runTest {
        val channel = RawSourceChannel(sourceOf(ByteArray(0)), EmptyCoroutineContext)
        channel.cancel(IOException("test cancellation"))
        assertFailsWith<CancellationException> {
            channel.awaitContent(1)
        }
    }

    @Test
    fun `cancel closes the underlying source`() = runTest {
        val source = TrackingSource(sourceOf(ByteArray(10)))
        val channel = RawSourceChannel(source, EmptyCoroutineContext)

        channel.cancel(IOException("test cancellation"))

        assertTrue(source.isClosed, "the source should be closed when the channel is cancelled")
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `awaitContent does not buffer more than CHANNEL_MAX_SIZE from a greedy source`() = runTest {
        val channel = RawSourceChannel(sourceOf(ByteArray(2 * CHANNEL_MAX_SIZE)), EmptyCoroutineContext)
        assertTrue(channel.awaitContent(1))

        assertTrue(
            channel.readBuffer.remaining <= CHANNEL_MAX_SIZE,
            "buffered ${channel.readBuffer.remaining} bytes, expected at most $CHANNEL_MAX_SIZE"
        )
    }

    @Test
    fun `ByteReadChannel factory reads the whole source`() = runTest {
        val channel = ByteReadChannel(sourceOf(ByteArray(10) { it.toByte() }), EmptyCoroutineContext)

        assertContentEquals(ByteArray(10) { it.toByte() }, channel.readBuffer().readByteArray())
    }

    @Test
    fun `cancelling the parent job closes the source`() = runTest {
        val source = TrackingSource(sourceOf(ByteArray(10)))
        val parent = Job()

        ByteReadChannel(source, parent)
        parent.cancel()

        assertTrue(source.isClosed, "the source should be closed when the parent job is cancelled")
    }

    private fun sourceOf(content: ByteArray): RawSource = Buffer().apply { write(content) }

    private class TrackingSource(private val delegate: RawSource) : RawSource {
        var isClosed: Boolean = false
            private set

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = delegate.readAtMostTo(sink, byteCount)

        override fun close() {
            isClosed = true
            delegate.close()
        }
    }
}
