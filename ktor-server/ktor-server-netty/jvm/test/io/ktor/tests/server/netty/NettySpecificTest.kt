/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.BindException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class NettySpecificTest {

    @Test
    fun testNoLeakWithoutStartAndStop() = runTestWithRealTime {
        repeat(100000) {
            embeddedServer(Netty, serverConfig { })
        }
    }

    // Doesn't work with real time
    @Test
    fun testStartOnUsedPort() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val server = embeddedServer(Netty, port) {}

        try {
            server.start(wait = false)
        } catch (_: BindException) {
        }

        assertTrue(server.engine.bootstraps.all { (it.config().group() as ExecutorService).isTerminated })
    }

    @Test
    fun testStartMultipleConnectorsOnUsedPort() = runTestWithRealTime {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val socket2 = ServerSocket(0)
        val port2 = socket2.localPort
        val host = "0.0.0.0"

        socket.close()

        socket2.use {
            val environment = applicationEnvironment()

            val server = embeddedServer(Netty, environment, {
                connector {
                    this.port = port
                    this.host = host
                }
                connector {
                    this.port = port2
                    this.host = host
                }
            })

            try {
                server.start(wait = false)
            } catch (_: BindException) {
            }

            assertTrue(server.engine.bootstraps.all { (it.config().group() as ExecutorService).isTerminated })
        }
    }

    @Test
    fun contentLengthAndTransferEncodingAreSafelyRemoved() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()
        val testScope = CoroutineScope(coroutineContext)
        val earlyHints = HttpStatusCode(103, "Early Hints")

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                install(
                    createApplicationPlugin("CallLogging") {
                        on(ResponseSent) { call ->
                            testScope.launch {
                                val headers = call.response.headers.allValues()
                                assertNull(headers[HttpHeaders.ContentLength])
                                assertNull(headers[HttpHeaders.TransferEncoding])
                            }
                        }
                    },
                )

                routing {
                    get("/no-content") {
                        call.respond(HttpStatusCode.NoContent)
                    }

                    get("/no-content-channel-writer") {
                        call.respondBytesWriter(status = HttpStatusCode.NoContent) {}
                    }

                    get("/no-content-read-channel") {
                        call.respond(object : OutgoingContent.ReadChannelContent() {
                            override val status: HttpStatusCode = HttpStatusCode.NoContent
                            override fun readFrom(): ByteReadChannel = ByteReadChannel.Empty
                        })
                    }

                    get("/info") {
                        call.respond(earlyHints)
                    }

                    get("/info-channel-writer") {
                        call.respondBytesWriter(status = earlyHints) {}
                    }

                    get("/info-read-channel") {
                        call.respond(object : OutgoingContent.ReadChannelContent() {
                            override val status: HttpStatusCode = earlyHints
                            override fun readFrom(): ByteReadChannel = ByteReadChannel.Empty
                        })
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(10.seconds) {
                appStarted.await()
            }
            val connector = serverApp.engine.resolvedConnectors()[0]
            val host = connector.host
            val port = connector.port

            HttpClient(CIO) {
                install(DefaultRequest) {
                    url("http://$host:$port/")
                }
            }.use { client ->
                assertEquals(HttpStatusCode.NoContent, client.get("/no-content").status)
                assertEquals(HttpStatusCode.NoContent, client.get("/no-content-channel-writer").status)
                assertEquals(HttpStatusCode.NoContent, client.get("/no-content-read-channel").status)
                assertEquals(earlyHints, client.get("/info").status)
                assertEquals(earlyHints, client.get("/info-channel-writer").status)
                assertEquals(earlyHints, client.get("/info-read-channel").status)
            }
        } finally {
            serverJob.cancel()
        }
    }

    @Test
    fun badRequestOnInvalidQueryString() = runTestWithRealTime {
        val appStarted = CompletableDeferred<Application>()

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    get {
                        call.request.queryParameters.entries()
                    }
                }
            }

            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }

            server.start(wait = true)
        }

        val serverApp = withTimeout(10.seconds) {
            appStarted.await()
        }
        val connector = serverApp.engine.resolvedConnectors()[0]

        try {
            SelectorManager().use { manager ->
                aSocket(manager).tcp().connect(connector.host, connector.port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    writeChannel.writeStringUtf8("GET /?%s% HTTP/1.1\r\n")
                    writeChannel.writeStringUtf8("Host: ${connector.host}:${connector.port}\r\n")
                    writeChannel.writeStringUtf8("Connection: close\r\n\r\n")
                    writeChannel.flush()

                    withTimeout(5000) {
                        readChannel.awaitContent()

                        val responseLines = mutableListOf<String>()
                        assertFalse(readChannel.isClosedForRead)
                        while (!readChannel.isClosedForRead) {
                            val line = readChannel.readLine() ?: break
                            responseLines.add(line)
                        }

                        assertTrue(responseLines.isNotEmpty())
                        assertEquals("HTTP/1.1 400 Bad Request", responseLines.first())
                    }
                }
            }
        } finally {
            serverJob.cancel()
        }
    }
}
