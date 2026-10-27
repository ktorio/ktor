/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class UrlConnectionUtilsTest {

    private val data = HttpRequestBuilder().build()

    @Test
    fun `timeout-aware connection catches error in connect`() = runTest {
        val connection = TestConnection(throwInConnect = true)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.connect()
            }
        }
    }

    @Test
    fun `timeout-aware connection catches error in response status code`() = runTest {
        val connection = TestConnection(throwInResponseCode = true)
        assertFailsWith<Throwable>("Connect timeout has expired") {
            connection.timeoutAwareConnection(data) {
                it.responseCode
            }
        }
    }

    @Test
    fun `disconnects immediately on cancellation`() = runTest {
        val job = Job(coroutineContext.job)
        val connection = TestConnection()
        connection.disconnectOnCancellation(job)

        // Keep the job cancelling while asserting that disconnect runs before child completion.
        CoroutineScope(coroutineContext + job).launch(start = CoroutineStart.UNDISPATCHED) {
            awaitCancellation()
        }

        job.cancel()

        assertFalse(job.isCompleted)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `does not disconnect on completion`() {
        val job = Job()
        val connection = TestConnection()
        connection.disconnectOnCancellation(job)

        job.complete()

        assertEquals(0, connection.disconnectCount)
    }

    @Test
    fun `call completion waits for stream closure after cancellation`() = runResponseTest {
        stream.pauseClose()
        createBody()

        callJob.cancel()
        stream.closeStarted.await()

        assertFalse(callJob.isCompleted, "The call must wait until stream closure ends")

        stream.releaseClose()
        callJob.join()
        stream.assertClosed()
    }

    @Test
    fun `stream closes when call is cancelled before content creation`() = runResponseTest {
        callJob.cancel()

        val body = createBody()
        stream.closeFinished.await()

        assertNotNull(body.closedCause)
        stream.assertClosed()
    }

    @Test
    fun `cancelling call closes stream only after active read returns`() = runResponseTest {
        stream.pauseRead()
        val body = createBody()
        val consumer = async(Dispatchers.IO) { runCatching { body.readByte() } }
        stream.readStarted.await()

        callJob.cancel()

        assertFalse(callJob.isCompleted, "The call must wait for its reader to unwind")
        assertFalse(stream.closeStarted.isCompleted, "Disconnect must not close a stream still being read")

        stream.releaseRead()
        assertIs<CancellationException>(consumer.await().exceptionOrNull())
        stream.closeFinished.await()

        assertFalse(stream.closedDuringRead)
    }

    @Test
    fun `cancelling body disconnects connection and unblocks reader`() = runResponseTest {
        stream.pauseRead()
        connection.onDisconnect = stream::releaseRead
        val body = createBody()
        val consumer = async(Dispatchers.IO) { runCatching { body.readByte() } }
        stream.readStarted.await()

        body.cancel()

        assertIs<CancellationException>(consumer.await().exceptionOrNull())
        assertTrue(callJob.isCancelled)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `body cancellation does not wait for an ongoing disconnect`() = runResponseTest {
        pauseDisconnect()
        val body = createBody()
        val cancellation = async(Dispatchers.IO) { callJob.cancel() }
        disconnectStarted.await()

        body.cancel()

        assertNotNull(body.closedCause)
        assertFalse(disconnectFinished.isCompleted)

        releaseDisconnect()
        cancellation.await()
    }

    @Test
    fun `body cancellation preserves its cause in the call`() = runResponseTest {
        val body = createBody()
        val failure = IOException("Body cancelled")
        val callFailure = CompletableDeferred<Throwable?>()
        callJob.invokeOnCompletion { callFailure.complete(it) }

        body.cancel(failure)
        callJob.join()

        assertCausedBy(failure, callFailure.await())
    }

    @Test
    fun `cancellation after underlying EOF still cancels the call`() = runResponseTest {
        val body = ByteChannel()
        val responseStream = AndroidResponseInputStream(stream, callJob).apply { channel = body }

        assertEquals(-1, responseStream.read())
        // Cancellation wins before the adapter records normal EOF and invokes close.
        body.cancel(CancellationException("Body cancelled at EOF"))
        responseStream.close()

        assertTrue(callJob.isCancelled, "Channel cancellation must take precedence over stream EOF")
    }

    @Test
    fun `EOF exception closes stream without cancelling the call`() = runResponseTest(
        TestResponseStream(readFailure = EOFException())
    ) {
        val body = createBody()

        assertFalse(body.awaitContent())

        stream.assertClosed()
        assertTrue(callJob.isActive)
        assertEquals(0, connection.disconnectCount)
    }

    @Test
    fun `close failure at EOF does not fail the body`() = runResponseTest(
        TestResponseStream(closeFailure = IOException("Close failed"))
    ) {
        val body = createBody()

        assertFalse(body.awaitContent())

        stream.assertClosed()
        assertTrue(callJob.isActive)
    }

    @Test
    fun `stream cleanup failure does not escape cancellation`() = runResponseTest(
        TestResponseStream(closeFailure = IOException("Close failed"))
    ) {
        val body = createBody()

        body.cancel()
        callJob.join()

        stream.assertClosed()
    }

    @Test
    fun `cancelling partially read response aborts call without reading ahead`() = runResponseTest(
        TestResponseStream(ByteArray(128 * 1024))
    ) {
        val body = createBody()

        assertEquals(0, stream.readCount.get())
        assertTrue(body.awaitContent())

        body.cancel()
        stream.closeFinished.await()

        assertTrue(callJob.isCancelled)
        assertEquals(1, stream.readCount.get())
    }

    @Test
    fun `consuming response closes stream without disconnecting`() {
        val expected = byteArrayOf(1, 2, 3, 4)
        runResponseTest(TestResponseStream(expected)) {
            val body = createBody()

            assertContentEquals(expected, body.toByteArray())
            stream.assertClosed()

            body.cancel()
            assertTrue(callJob.isActive, "Clean EOF must not cancel the call")
            assertEquals(0, connection.disconnectCount)
        }
    }

    @Test
    fun `read failure propagates to body and cancellation closes stream`() {
        val failure = IOException("Read failed")
        runResponseTest(TestResponseStream(readFailure = failure)) {
            val body = createBody()

            val actual = assertFailsWith<IOException> { body.readByte() }
            assertCausedBy(failure, actual)

            callJob.cancelAndJoin()
            stream.assertClosed()
        }
    }
}

private fun runResponseTest(
    stream: TestResponseStream = TestResponseStream(),
    block: suspend ResponseFixture.() -> Unit
) = runTest(timeout = 5.seconds) {
    val fixture = ResponseFixture(stream, this)
    val operation = async(Dispatchers.IO) { fixture.block() }
    try {
        operation.await()
    } finally {
        fixture.shutdown(operation)
    }
}

private fun assertCausedBy(expected: Throwable, actual: Throwable?) {
    assertTrue(
        generateSequence(actual) { it.cause }.any { it === expected },
        "Expected $expected in the cause chain of $actual"
    )
}

private class ResponseFixture(
    val stream: TestResponseStream,
    testScope: TestScope
) : CoroutineScope by testScope {
    val callJob = Job(testScope.backgroundScope.coroutineContext.job)
    val connection = TestConnection(inputStream = stream).apply { disconnectOnCancellation(callJob) }
    val disconnectStarted = CompletableDeferred<Unit>()
    val disconnectFinished = CompletableDeferred<Unit>()
    private val allowDisconnectToFinish = CountDownLatch(1)

    fun createBody(): ByteReadChannel = connection.content(200, callJob + Dispatchers.IO)

    fun pauseDisconnect() {
        connection.onDisconnect = {
            disconnectStarted.complete(Unit)
            check(allowDisconnectToFinish.await(10, TimeUnit.SECONDS)) { "Disconnect was not released" }
            disconnectFinished.complete(Unit)
        }
    }

    fun releaseDisconnect() = allowDisconnectToFinish.countDown()

    suspend fun shutdown(operation: Job) {
        releaseDisconnect()
        stream.releaseRead()
        stream.releaseClose()
        withContext(NonCancellable + Dispatchers.Default) {
            withTimeout(5.seconds) {
                operation.cancelAndJoin()
                callJob.cancelAndJoin()
                if (stream.closeStarted.isCompleted) stream.closeFinished.await()
            }
        }
    }
}

private class TestResponseStream(
    bytes: ByteArray = byteArrayOf(),
    private val readFailure: IOException? = null,
    private val closeFailure: IOException? = null
) : ByteArrayInputStream(bytes) {
    val readCount = AtomicInteger()
    private val closeCount = AtomicInteger()
    val readStarted = CompletableDeferred<Unit>()
    val closeStarted = CompletableDeferred<Unit>()
    val closeFinished = CompletableDeferred<Unit>()
    private val allowReadToFinish = CountDownLatch(1)
    private val allowCloseToFinish = CountDownLatch(1)

    @Volatile
    private var readPaused = false

    @Volatile
    private var closePaused = false

    @Volatile
    private var reading = false

    @Volatile
    var closedDuringRead = false
        private set

    fun pauseRead() {
        readPaused = true
    }

    fun releaseRead() = allowReadToFinish.countDown()

    fun pauseClose() {
        closePaused = true
    }

    fun releaseClose() = allowCloseToFinish.countDown()

    fun assertClosed() {
        assertTrue(closeFinished.isCompleted, "Stream closure must have finished")
        assertEquals(1, closeCount.get(), "Stream must be closed exactly once")
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        readCount.incrementAndGet()
        reading = true
        readStarted.complete(Unit)
        try {
            if (readPaused) {
                check(allowReadToFinish.await(10, TimeUnit.SECONDS)) { "Response reader was not released" }
                throw IOException("Disconnected")
            }
            readFailure?.let { throw it }
            return super.read(bytes, offset, length)
        } finally {
            reading = false
        }
    }

    override fun close() {
        closedDuringRead = reading
        closeCount.incrementAndGet()
        closeStarted.complete(Unit)
        try {
            if (closePaused) {
                check(allowCloseToFinish.await(10, TimeUnit.SECONDS)) { "Stream closure was not released" }
            }
            closeFailure?.let { throw it }
            super.close()
        } finally {
            closeFinished.complete(Unit)
        }
    }
}

private class TestConnection(
    private val throwInConnect: Boolean = false,
    private val throwInResponseCode: Boolean = false,
    private val inputStream: InputStream? = null,
) : HttpURLConnection(URL("https://example.com")) {

    var disconnectCount: Int = 0
        private set
    var onDisconnect: () -> Unit = {}

    override fun getResponseCode(): Int {
        if (throwInResponseCode) throw ConnectException("Connect timed out")
        return 200
    }

    override fun connect() {
        if (throwInConnect) throw SocketTimeoutException()
    }

    override fun getInputStream(): InputStream = inputStream ?: super.getInputStream()

    override fun disconnect() {
        disconnectCount++
        onDisconnect()
    }

    override fun usingProxy(): Boolean {
        throw NotImplementedError()
    }
}
