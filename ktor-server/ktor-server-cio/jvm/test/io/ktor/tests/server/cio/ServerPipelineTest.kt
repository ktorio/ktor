/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.server.cio.backend.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
class ServerPipelineTest : CoroutineScope {

    private val dispatcher = Dispatchers.IO.limitedParallelism(8)

    private val job: CompletableJob = SupervisorJob()
    private var name: CoroutineName = CoroutineName("PipelineTest")

    override val coroutineContext: CoroutineContext by lazy {
        (dispatcher as CoroutineContext) + (job as CoroutineContext.Element) + (name as AbstractCoroutineContextElement)
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        name = CoroutineName("PipelineTest:${testInfo.testMethod.map { it.name }.orElse("")}")
    }

    @AfterEach
    fun cleanup() {
        job.cancel()
        runBlocking {
            job.join()
        }
    }

    @Test
    fun testSmoke(): Unit = runBlocking {
        val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
        val job = startServerConnectionPipeline(connection, timeout = Duration.INFINITE) {
            error("Shouldn't reach here")
        }

        job.cancel()
    }

    @Test
    fun testSingleRequest(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()

        val connection = ServerIncomingConnection(input, output, null, null)
        startServerConnectionPipeline(connection, timeout = Duration.INFINITE) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("close", request.headers[HttpHeaders.Connection].toString())

            assertNull(upgraded)

            request.release()

            output.writeStringUtf8("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nConnection: close\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 200 OK", output.readLineStrict())
        assertEquals("Connection: close", output.readLineStrict())
        assertEquals("", output.readLineStrict())
        assertEquals("/", requestsReceived.single())

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testSingleRequestUpgradeWithoutUpgrade(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()

        val connection = ServerIncomingConnection(input, output, null, null)
        startServerConnectionPipeline(connection, timeout = Duration.INFINITE) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("Upgrade", request.headers[HttpHeaders.Connection].toString())

            assertNotNull(upgraded)
            upgraded.complete(true)

            request.release()

            output.writeStringUtf8("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 200 OK", output.readLineStrict())
        assertEquals("Connection: close", output.readLineStrict())
        assertEquals("", output.readLineStrict())
        assertEquals("/", requestsReceived.single())

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testPipelineIdleTimeoutNoRequests(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val connection = ServerIncomingConnection(input, output, null, null)
        supervisorScope {
            startServerConnectionPipeline(connection, Duration.ZERO) {
                error("Shouldn't reach here")
            }

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test-specific thing
            launch(CoroutineName("IO helper")) {
                try {
                    output.discard()
                } finally {
                    input.close()
                }
            }
        }
    }

    @Test
    fun testPipelineIdleTimeoutAfterRequests(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestHandled = Job()

        val connection = ServerIncomingConnection(input, output, null, null)
        supervisorScope {
            startServerConnectionPipeline(connection, timeout = 100.milliseconds) { request ->
                requestHandled.complete()
                request.release()
                input.cancel()
                output.close()
            }

            // send a single request
            input.writeStringUtf8("GET / HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
            input.flush()

            // after processing the request, the idle timeout machinery should cancel all the stuff
            requestHandled.join()

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test-specific thing
            launch(CoroutineName("IO helper")) {
                try {
                    output.discard()
                } finally {
                    input.close()
                }
            }
        }
    }

    @Test
    fun testRequestAfterIdleTimeoutIsNotHandled(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()
        val lateRequestHandled = CompletableDeferred<Unit>()

        val pipelineJob = startServerConnectionPipeline(
            ServerIncomingConnection(input, output, null, null),
            timeout = 100.milliseconds
        ) { request ->
            val isFirstRequest = request.uri.toString() == "/first"
            request.release()
            if (isFirstRequest) {
                this.output.writeStringUtf8("OK")
            } else {
                lateRequestHandled.complete(Unit)
            }
        }
        val outputReader = launch { output.discard() }

        input.writeStringUtf8("GET /first HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
        input.flush()
        withTimeout(1.seconds) { outputReader.join() }

        input.writeStringUtf8("GET /second HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
        input.flush()
        input.close()

        withTimeout(1.seconds) { pipelineJob.join() }
        assertFalse(lateRequestHandled.isCompleted, "Request should not be handled after the idle timeout")
        assertFalse(pipelineJob.isCancelled, "Pipeline job should complete gracefully")
    }

    @Test
    fun testWriterTimeoutCancelsQueuedResponses(): Unit = runBlocking(coroutineContext) {
        val responses = List(3) { ByteChannel() }
        val actorChannel = Channel<ByteReadChannel>(3)
        responses.forEach { assertTrue(actorChannel.trySend(it).isSuccess) }

        startServerPipelineWriter(
            actorChannel,
            timeout = Duration.ZERO,
            connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
        ).join()

        responses.forEach { assertTrue(it.isClosedForRead) }
    }

    @Test
    fun testIdleTimeoutDoesNotCancelActiveRequest(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()
        val responseSent = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        val requestJob = CompletableDeferred<Job>()

        val pipelineJob = startServerConnectionPipeline(
            ServerIncomingConnection(input, output, null, null),
            timeout = 100.milliseconds
        ) { request ->
            request.release()
            requestJob.complete(currentCoroutineContext().job)
            this.output.writeStringUtf8("OK")
            this.output.flushAndClose()
            responseSent.complete(Unit)
            finishRequest.await()
        }
        val outputReader = launch { output.discard() }

        input.writeStringUtf8("GET / HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
        input.flush()
        responseSent.await()
        withTimeout(1.seconds) { outputReader.join() }

        input.writeStringUtf8("GET /late HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
        input.flush()
        input.close()

        assertNull(withTimeoutOrNull(500.milliseconds) { requestJob.await().join() }, "Request should not be cancelled")
        finishRequest.complete(Unit)
        withTimeout(1.seconds) { requestJob.await().join() }
        withTimeout(1.seconds) { pipelineJob.join() }
        assertFalse(pipelineJob.isCancelled, "Pipeline job should complete gracefully")
    }

    @Test
    fun testParentJobAndTimerCancellation() {
        val l = CountDownLatch(1)

        val root = launch(coroutineContext) {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, timeout = Duration.INFINITE) {
                error("Shouldn't reach here")
            }
            l.countDown()
        }

        l.await()
        Thread.sleep(100)

        runBlocking {
            root.cancel()

            root.join()
        }
    }

    @Test
    fun testParentJobAndTimeoutCancellation(): Unit = runBlocking(coroutineContext) {
        val l = Job()

        val root = launch {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, timeout = 10.milliseconds) {
                error("Shouldn't reach here")
            }
            l.complete()
        }

        l.join()
        delay(100) // we need this delay because launching a coroutine takes time

        delay(1)
        root.cancelAndJoin()
    }
}
