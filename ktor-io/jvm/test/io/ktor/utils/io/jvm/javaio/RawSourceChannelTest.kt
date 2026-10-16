/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RawSourceChannelTest {

    @Test
    fun `awaitContent returns false when channel is closed with empty buffer`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()

        // First call closes the channel internally (EOF)
        val firstResult = channel.awaitContent(1)
        assertFalse(firstResult, "awaitContent should return false on EOF")

        // Second call hits the closedToken != null path
        val secondResult = channel.awaitContent(1)
        assertFalse(secondResult, "awaitContent should return false when channel is already closed with empty buffer")
    }

    @Test
    fun `awaitContent returns true when channel is closed with data in buffer`() = runTest {
        val channel = ByteArray(10) { it.toByte() }.inputStream().toByteReadChannel()

        // Request more than available to trigger EOF and close the channel
        val firstResult = channel.awaitContent(20)
        assertFalse(firstResult, "awaitContent should return false when not enough data")

        // Channel is now closed, but 10 bytes remain in the buffer
        val secondResult = channel.awaitContent(1)
        assertTrue(secondResult, "awaitContent should return true when closed channel still has data in buffer")
    }

    @Test
    fun `awaitContent throws when channel is cancelled`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()
        channel.cancel(IOException("test cancellation"))
        assertFailsWith<CancellationException> {
            channel.awaitContent(1)
        }
    }

    @Test
    fun `cancelling channel does not wait for source close`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            channel.cancel()
            source.closeStarted.await()

            assertTrue(channel.isClosedForRead, "Channel should be closed")
            assertFalse(source.closeFinished.isCompleted, "Close should not be completed")
        }
    }

    @Test
    fun `cancelling parent does not wait for source close`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            parentJob.cancel()
            source.closeStarted.await()

            assertTrue(channel.isClosedForRead, "Channel should be closed")
            assertFalse(source.closeFinished.isCompleted, "Close should not be completed")
            assertFalse(parentJob.isCompleted, "Parent job should not be completed")
        }
    }

    @Test
    fun `parent completion waits for source close`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            channel.cancel()
            source.closeStarted.await()
            parentJob.complete()

            assertFalse(channel.job.isCompleted, "Channel job should not be completed")
            assertFalse(parentJob.isCompleted, "Parent job should not be completed")

            source.releaseClose()
            parentJob.join()
            assertTrue(channel.job.isCompleted, "Channel job should be completed")
        }
    }

    @Test
    fun `channel cancellation contains close failures`() = runTest(timeout = 5.seconds) {
        val failure = IOException("Close failed")
        withChannel(TestSource(closeFailure = failure)) {
            val cancellation = CancellationException("Test cancellation")
            channel.cancel(cancellation)
            channel.job.join()

            assertIs<CancellationException>(channel.closedCause)
            assertSame(cancellation, channel.closedCause?.cause)
            assertEquals(listOf(failure), cancellation.suppressedExceptions)
        }
    }

    @Test
    fun `parent cancellation contains close failures`() = runTest(timeout = 5.seconds) {
        val failure = IOException("Close failed")
        withChannel(TestSource(closeFailure = failure)) {
            val cancellation = CancellationException("Test cancellation")
            parentJob.cancel(cancellation)
            parentJob.join()

            assertIs<CancellationException>(channel.closedCause)
            assertSame(cancellation, channel.closedCause?.cause)
            assertEquals(listOf(failure), cancellation.suppressedExceptions)
        }
    }

    @Test
    fun `concurrent cancellation closes the source only once`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            val startCancellation = CompletableDeferred<Unit>()
            val cancellations = List(32) {
                launch(Dispatchers.Default) {
                    startCancellation.await()
                    channel.cancel()
                }
            }
            startCancellation.complete(Unit)
            cancellations.joinAll()

            source.releaseClose()
            channel.job.join()
            assertEquals(1, source.closeCount, "Source should be closed exactly once")
        }
    }

    @Test
    fun `source closes when context job is already cancelled`() = runTest(timeout = 5.seconds) {
        val source = TestSource().apply { pauseClose() }
        withChannel(source, parentJob = Job().apply { cancel() }) {
            source.closeStarted.await()
            assertFalse(source.closeFinished.isCompleted)
        }
        assertEquals(1, source.closeCount, "Source should be closed exactly once")
    }

    @Test
    fun `channel cancellation during EOF closes the source only once`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            val reader = async(Dispatchers.IO) { channel.awaitContent() }
            source.closeStarted.await()
            channel.cancel()
            source.releaseClose()

            assertFalse(reader.await())
            assertEquals(1, source.closeCount, "Source should be closed exactly once")
        }
    }

    @Test
    fun `parent cancellation during EOF closes the source only once`() = runTest(timeout = 5.seconds) {
        withChannel {
            source.pauseClose()
            val reader = async(Dispatchers.IO) { channel.awaitContent() }
            source.closeStarted.await()
            parentJob.cancel()
            source.releaseClose()

            assertFailsWith<CancellationException> { reader.await() }
            assertEquals(1, source.closeCount, "Source should be closed exactly once")
        }
    }

    @Test
    fun `EOF close failure completes the channel job`() = runTest(timeout = 5.seconds) {
        val failure = IOException("Close failed")
        withChannel(TestSource(closeFailure = failure)) {
            val actual = assertFailsWith<IOException> { channel.awaitContent() }

            assertEquals(failure.message, actual.message)
            assertSame(failure, channel.closedCause?.cause)
            assertTrue(channel.job.isCompleted, "Channel job should be completed")
            assertEquals(1, source.closeCount, "Source should be closed exactly once")
        }
    }

    @Test
    fun `cancellation can close sources when IO dispatcher is full`() = runTest(timeout = 5.seconds) {
        class ReadUntilClosedSource : RawSource {
            val readStarted = CompletableDeferred<Unit>()
            private val closed = CountDownLatch(1)

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                readStarted.complete(Unit)
                check(closed.await(10, TimeUnit.SECONDS)) { "Source was not closed" }
                throw IOException("Source was closed")
            }

            override fun close() = closed.countDown()
        }

        val parallelism = System.getProperty("kotlinx.coroutines.io.parallelism")?.toInt()
            ?: maxOf(64, Runtime.getRuntime().availableProcessors())
        val sources = List(parallelism) { ReadUntilClosedSource() }
        val channels = sources.map { RawSourceChannel(it, coroutineContext + Dispatchers.IO) }
        val readers = channels.map { channel -> async { channel.awaitContent() } }
        try {
            // Hold every normal IO worker in a read that needs close() to return.
            sources.forEach { it.readStarted.await() }
            channels.forEach { it.cancel() }

            readers.forEach { reader ->
                assertFailsWith<CancellationException> { reader.await() }
            }
        } finally {
            sources.forEach { it.close() }
            channels.forEach { it.job.cancel() }
        }
    }

    @Test
    fun `cancellation prevents another read after the active read returns`() = runTest {
        class PausedReadSource : RawSource {
            val readStarted = CompletableDeferred<Unit>()
            private val readCounter = AtomicInteger()
            private val allowReadToFinish = CountDownLatch(1)

            val readCount: Int get() = readCounter.get()

            fun releaseRead() = allowReadToFinish.countDown()

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                if (readCounter.incrementAndGet() > 1) return -1
                readStarted.complete(Unit)
                check(allowReadToFinish.await(10, TimeUnit.SECONDS)) { "Read was not released" }
                sink.writeByte(1)
                return 1
            }

            override fun close() {}
        }

        val source = PausedReadSource()
        val channel = RawSourceChannel(source, coroutineContext + Dispatchers.IO)
        val reader = async { channel.awaitContent(2) }
        try {
            source.readStarted.await()
            channel.cancel()
            source.releaseRead()

            assertFailsWith<CancellationException> { reader.await() }
            assertEquals(1, source.readCount, "Source should be read exactly once")
        } finally {
            source.releaseRead()
            channel.job.cancel()
        }
    }
}

/** Runs [block] and releases its resources on completion or cancellation. */
private suspend fun TestScope.withChannel(
    source: TestSource = TestSource(),
    parentJob: CompletableJob = Job(backgroundScope.coroutineContext.job),
    block: suspend ChannelFixture.() -> Unit
) {
    val fixture = ChannelFixture(source, parentJob)
    // Run on IO dispatcher to avoid blocking test thread
    val operation = async(Dispatchers.IO) { fixture.run(block) }
    try {
        operation.await()
    } finally {
        fixture.shutdown(operation)
    }
}

private class ChannelFixture(val source: TestSource, val parentJob: CompletableJob) : AutoCloseable {

    // Unconfined exposes cleanup that accidentally runs on the cancellation thread.
    lateinit var channel: RawSourceChannel

    suspend fun run(block: suspend ChannelFixture.() -> Unit) {
        use {
            channel = RawSourceChannel(source, parentJob + Dispatchers.Unconfined)
            block()
        }
    }

    override fun close() {
        source.releaseClose()
        parentJob.cancel()
    }

    suspend fun shutdown(operation: Job) {
        source.releaseClose()
        withContext(NonCancellable + Dispatchers.Default) {
            withTimeout(5.seconds) {
                operation.cancelAndJoin()
                if (source.closeStarted.isCompleted) source.closeFinished.await()
            }
        }
    }
}

private class TestSource(private val closeFailure: IOException? = null) : RawSource {
    val closeStarted = CompletableDeferred<Unit>()
    val closeFinished = CompletableDeferred<Unit>()
    private val closeCounter = AtomicInteger()
    private val allowCloseToFinish = CountDownLatch(1)

    @Volatile
    private var closePaused = false

    val closeCount: Int get() = closeCounter.get()

    fun pauseClose() {
        closePaused = true
    }

    fun releaseClose() = allowCloseToFinish.countDown()

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = -1

    override fun close() {
        closeCounter.incrementAndGet()
        closeStarted.complete(Unit)
        try {
            if (closePaused) {
                check(allowCloseToFinish.await(10, TimeUnit.SECONDS)) { "Close was not released" }
            }
            closeFailure?.let { throw it }
        } finally {
            closeFinished.complete(Unit)
        }
    }
}
