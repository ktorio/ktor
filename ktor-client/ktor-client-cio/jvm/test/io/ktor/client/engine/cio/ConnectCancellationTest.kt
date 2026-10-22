/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.*

/**
 * Checks that `Endpoint.connect` closes a connected socket and releases its connection permits
 * when it can't use the socket because the request was cancelled or the connect timeout expired
 * right after the socket connected.
 */
class ConnectCancellationTest {

    private val selectorManager = SelectorManager()
    private val endpointJob = SupervisorJob()
    private val serverSocket = ServerSocket(0)

    // Completes with true if the client closed the first accepted connection.
    private val firstConnectionClosed = CompletableDeferred<Boolean>()

    private val serverThread = thread(isDaemon = true) {
        try {
            serverSocket.accept().use { socket ->
                socket.soTimeout = SOCKET_READ_TIMEOUT
                val closed = try {
                    socket.getInputStream().read() == -1
                } catch (_: SocketTimeoutException) {
                    false
                } catch (_: SocketException) {
                    // A reset also means the client is gone
                    true
                }
                firstConnectionClosed.complete(closed)
            }

            while (true) {
                serverSocket.accept().use { socket ->
                    socket.soTimeout = SOCKET_READ_TIMEOUT
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 2\r\n\r\nOK".toByteArray()
                    )
                    // Half-close and drain the request: close() with unread input sends RST instead of FIN
                    socket.shutdownOutput()
                    try {
                        socket.getInputStream().readBytes()
                    } catch (_: SocketTimeoutException) {
                        // The client kept the connection open; the response is already written
                    }
                }
            }
        } catch (_: SocketException) {
        } finally {
            firstConnectionClosed.complete(false)
        }
    }

    @AfterTest
    fun teardown() {
        endpointJob.cancel()
        serverSocket.close()
        serverThread.join(SERVER_THREAD_JOIN_TIMEOUT)
        assertFalse(serverThread.isAlive, "The server thread didn't stop")
        selectorManager.close()
    }

    @Test
    fun `releases connection when request is cancelled right after socket connects`() = runTestWithRealTime {
        lateinit var request: Job
        val endpoint = createEndpoint(afterFirstConnect = { request.cancel() })

        request = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            endpoint.execute(requestData(), coroutineContext)
        }
        request.join()
        assertTrue(request.isCancelled)

        assertEquals("OK", endpoint.requestText())
        assertTrue(firstConnectionClosed.await(), "The cancelled request's socket wasn't closed")
    }

    @Test
    fun `releases connection when connect timeout expires right after socket connects`() = runTestWithRealTime {
        val endpoint = createEndpoint(
            afterFirstConnect = {
                // Hold the connected socket until the connect timeout has cancelled the `withTimeout` block.
                // A child job is cancelled together with it. `suspendCoroutine` resumes even though the block
                // is cancelled, where a cancellable wait would throw and the socket would never be returned.
                val connectTimedOut = Job(parent = currentCoroutineContext().job)
                suspendCoroutine { continuation ->
                    connectTimedOut.invokeOnCompletion { continuation.resume(Unit) }
                }
            }
        )

        assertFailsWith<ConnectTimeoutException> {
            withContext(Dispatchers.IO) {
                endpoint.execute(requestData(), coroutineContext)
            }
        }

        assertEquals("OK", endpoint.requestText())
        assertTrue(firstConnectionClosed.await(), "The timed out request's socket wasn't closed")
    }

    /**
     * Creates an endpoint whose connections share a single permit.
     * [afterFirstConnect] runs once, after the first socket has connected and before it is returned
     * from [ConnectionFactory.connect].
     */
    @OptIn(InternalAPI::class)
    private fun createEndpoint(afterFirstConnect: suspend () -> Unit): Endpoint {
        val intercepted = AtomicBoolean(false)
        val connectionFactory = ConnectionFactory(
            selectorManager,
            connectionsLimit = 1,
            addressConnectionsLimit = 1,
            openSocket = { selector, address, configuration ->
                aSocket(selector).tcp().connect(address, configuration).also {
                    if (intercepted.compareAndSet(false, true)) {
                        afterFirstConnect()
                    }
                }
            }
        )

        return Endpoint(
            host = TEST_SERVER_HOST,
            port = serverSocket.localPort,
            proxy = null,
            secure = false,
            config = CIOEngineConfig().apply { endpoint.connectTimeout = CONNECT_TIMEOUT },
            connectionFactory = connectionFactory,
            coroutineContext = Dispatchers.IO + endpointJob,
            onDone = {},
            unixSocket = null,
        )
    }

    private suspend fun Endpoint.requestText(): String = withContext(Dispatchers.IO) {
        val response = execute(requestData(), coroutineContext)
        (response.body as ByteReadChannel).toByteArray().decodeToString()
    }

    private fun requestData() = HttpRequestBuilder().apply {
        url("http://$TEST_SERVER_HOST:${serverSocket.localPort}/")
    }.build()

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
        private const val CONNECT_TIMEOUT: Long = 1000
        private const val SOCKET_READ_TIMEOUT = 5000
        private const val SERVER_THREAD_JOIN_TIMEOUT: Long = 10_000
    }
}
