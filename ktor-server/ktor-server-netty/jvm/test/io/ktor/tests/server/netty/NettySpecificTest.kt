/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.*
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.net.*
import java.util.concurrent.*
import kotlin.test.*

class NettySpecificTest {

    @Test
    fun testNoLeakWithoutStartAndStop() {
        repeat(100000) {
            embeddedServer(Netty, serverConfig { })
        }
    }

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
    fun testStartMultipleConnectorsOnUsedPort() {
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
    fun contentLengthAndTransferEncodingAreSafelyRemoved() = runTest {
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

                    get("no-content-channel-writer") {
                        call.respondBytesWriter(status = HttpStatusCode.NoContent) {}
                    }

                    get("no-content-read-channel") {
                        call.respond(object : OutgoingContent.ReadChannelContent() {
                            override val status: HttpStatusCode = HttpStatusCode.NoContent
                            override fun readFrom(): ByteReadChannel = ByteReadChannel.Empty
                        })
                    }

                    get("/info") {
                        call.respond(earlyHints)
                    }

                    get("info-channel-writer") {
                        call.respondBytesWriter(status = earlyHints) {}
                    }

                    get("info-read-channel") {
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
            val serverApp = appStarted.await()
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
}
