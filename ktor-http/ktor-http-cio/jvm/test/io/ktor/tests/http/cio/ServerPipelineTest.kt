/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.server.cio.backend.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import kotlinx.coroutines.scheduling.*
import org.junit.*
import org.junit.rules.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.test.Test

class ServerPipelineTest : CoroutineScope {
    @get:Rule
    val testName = TestName()

    @OptIn(InternalCoroutinesApi::class)
    private val dispatcher = ExperimentalCoroutineDispatcher(8)

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext by lazy {
        CoroutineName("PipelineTest.${testName.methodName}") + job + dispatcher
    }

    @get:Rule
    val timeout = CoroutinesTimeout(2000L, true)

    @OptIn(InternalCoroutinesApi::class)
    @AfterTest
    fun cleanup() {
        job.cancel()
        runBlocking {
            job.join()
        }
        job.invokeOnCompletion {
            dispatcher.close()
        }
    }

    @Test
    fun testSmoke(): Unit = runBlocking {
        val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
        val queue = WeakTimeoutQueue(10L) { 1L }
        val job = startServerConnectionPipeline(connection, queue) {
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
        val queue = WeakTimeoutQueue(10L) { 1L }
        startServerConnectionPipeline(connection, queue) { request ->
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

        assertEquals("HTTP/1.1 200 OK", output.readUTF8Line())
        assertEquals("Connection: close", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
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
        val queue = WeakTimeoutQueue(10L) { 1L }
        startServerConnectionPipeline(connection, queue) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("Upgrade", request.headers[HttpHeaders.Connection].toString())

            assertNotNull(upgraded)
            upgraded!!.complete(true)

            request.release()

            output.writeStringUtf8("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 200 OK", output.readUTF8Line())
        assertEquals("Connection: close", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
        assertEquals("/", requestsReceived.single())

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testSingleRequestUpgradeNoTimeout(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()
        val latch = Job()

        val connection = ServerIncomingConnection(input, output, null, null)
        val queue = WeakTimeoutQueue(1L)
        startServerConnectionPipeline(connection, queue) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("Upgrade", request.headers[HttpHeaders.Connection].toString())

            assertNotNull(upgraded)
            upgraded!!.complete(true)

            request.release()

            output.writeStringUtf8("HTTP/1.1 101 Switching\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
            output.flush()

            latch.join()
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 101 Switching", output.readUTF8Line())
        assertEquals("Upgrade: test", output.readUTF8Line())
        assertEquals("Connection: Upgrade", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
        assertEquals("/", requestsReceived.single())

        delay(100)
        queue.process() // shouldn't be cancelled here because it is upgraded and running
        latch.complete()

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testPipelineIdleTimeoutNoRequests(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val clock = AtomicLong(1L)

        val connection = ServerIncomingConnection(input, output, null, null)
        val queue = WeakTimeoutQueue(10L) { clock.get() }
        supervisorScope {
            val job = startServerConnectionPipeline(connection, queue) {
                error("Shouldn't reach here")
            }

            // the timeout queue is only working when there is at least small activity
            launch(CoroutineName("poller")) {
                do {
                    queue.process()
                    delay(50)
                    clock.addAndGet(11L)
                } while (job.isActive)
            }

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test specific thing
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

        val clock = AtomicLong(1L)
        val requestHandled = Job()

        val connection = ServerIncomingConnection(input, output, null, null)
        val queue = WeakTimeoutQueue(10L) { clock.get() }
        supervisorScope {
            val job = startServerConnectionPipeline(connection, queue) { request ->
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

            // the timeout queue is only working when there is at least small activity
            launch(CoroutineName("poller")) {
                do {
                    queue.process()
                    delay(50)
                    clock.addAndGet(11L)
                } while (job.isActive)
            }

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test specific thing
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
    fun testParentJobAndTimerCancellation() {
        val l = CountDownLatch(1)
        val queue = WeakTimeoutQueue(100000L)

        val root = launch(coroutineContext) {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, queue) {
                error("Shouldn't reach here")
            }
            l.countDown()
        }

        l.await()
        Thread.sleep(100)

        runBlocking {
            root.cancel()
            queue.cancel()

            root.join()
        }
    }

    @Test
    fun testParentJobAndTimeoutCancellation(): Unit = runBlocking(coroutineContext) {
        val l = Job()
        val queue = WeakTimeoutQueue(10L)

        val root = launch {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, queue) {
                error("Shouldn't reach here")
            }
            l.complete()
        }

        l.join()
        delay(100) // we need this delay because launching a coroutine takes time

        launch {
            queue.cancel()
        }

        delay(1)
        root.cancelAndJoin()
    }
}
