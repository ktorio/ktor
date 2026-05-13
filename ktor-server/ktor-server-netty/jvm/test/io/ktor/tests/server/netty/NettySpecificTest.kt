/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.netty.http1.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.mockk.mockk
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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
    fun `start cleans up resources on non-BindException failure`() {
        val server = embeddedServer(Netty, port = 70000) {}

        assertFailsWith<IllegalArgumentException> {
            server.start(wait = false)
        }

        assertTrue(
            server.engine.bootstraps.all { (it.config().group() as ExecutorService).isTerminated },
            "event loop groups must be terminated when bind fails with a non-BindException"
        )
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
    fun `call finishes when channel becomes inactive before response is sent`() = runTestWithRealTime {
        val handlerStarted = CompletableDeferred<Unit>()
        val shouldRespond = CompletableDeferred<Unit>()
        val callFinished = CompletableDeferred<Unit>()
        val appStarted = CompletableDeferred<Application>()
        val channel: AtomicReference<Channel> = AtomicReference(null)

        val serverJob = launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 0) {
                routing {
                    get("/test") {
                        handlerStarted.complete(Unit)
                        channel.set((call.pipelineCall.engineCall as NettyApplicationCall).context.channel())
                        shouldRespond.await()

                        // responseReady is only completed once call.respond() runs;
                        // if it is never completed, finishedEvent is never set and
                        // responseWriteJob.join() in call.finish() hangs forever.
                        (call.pipelineCall.engineCall as NettyApplicationCall).finishedEvent.addListener {
                            callFinished.complete(Unit)
                        }

                        call.respond(HttpStatusCode.OK, "Hello")
                    }
                }
            }
            server.monitor.subscribe(ApplicationStarted) { app ->
                appStarted.complete(app)
            }
            server.start(wait = true)
        }

        try {
            val serverApp = withTimeout(10.seconds) { appStarted.await() }
            val connector = serverApp.engine.resolvedConnectors()[0]

            SelectorManager().use { manager ->
                val socket = aSocket(manager).tcp().connect(connector.host, connector.port)
                val writeChannel = socket.openWriteChannel()
                writeChannel.writeStringUtf8("GET /test HTTP/1.1\r\n")
                writeChannel.writeStringUtf8("Host: ${connector.host}:${connector.port}\r\n")
                writeChannel.writeStringUtf8("Connection: close\r\n\r\n")
                writeChannel.flush()

                // Wait until the handler is running, then close the connection
                withTimeout(5.seconds) { handlerStarted.await() }
                socket.close()
            }

            // Give Netty time to process the channel-inactive event
            withTimeout(20.seconds) {
                while (channel.get() == null || channel.get().isActive) {
                    delay(10.milliseconds)
                }
            }

            // Now let the handler try to respond to the already-closed channel
            shouldRespond.complete(Unit)

            // finishedEvent must be resolved (success or failure) — if responseReady is
            // never completed, this deferred hangs forever and the test times out
            withTimeout(20.seconds) { callFinished.await() }
        } finally {
            serverJob.cancel()
        }
    }

    @Test
    fun `client disconnect is logged at trace level`() {
        val logger = LoggerFactory.getLogger("io.ktor.tests.server.netty.ClientDisconnectLogging") as Logger
        val previousLevel = logger.level
        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.TRACE
        logger.addAppender(listAppender)

        val environment = applicationEnvironment { log = logger }
        val callEventGroup = NioEventLoopGroup(1)
        val handler = NettyHttp1Handler(
            applicationProvider = { mockk(relaxed = true) },
            enginePipeline = mockk(relaxed = true),
            environment = environment,
            callEventGroup = callEventGroup,
            engineContext = EmptyCoroutineContext,
            userContext = EmptyCoroutineContext,
            runningLimit = 32
        )

        // EmbeddedChannel without firing the full pipeline lifecycle: we only
        // want to exercise exceptionCaught. Use a fresh channel and add only
        // this handler so RequestBodyHandler isn't installed via channelActive.
        val channel = EmbeddedChannel()
        channel.pipeline().addLast(handler)
        try {
            channel.pipeline().fireExceptionCaught(IOException("Connection reset by peer"))

            val ioOpFailedEvents = listAppender.list.filter { it.formattedMessage == "I/O operation failed" }
            assertEquals(
                1,
                ioOpFailedEvents.size,
                "Expected one 'I/O operation failed' log entry for client-disconnect IOException, " +
                    "got ${ioOpFailedEvents.size}"
            )
            assertEquals(
                Level.TRACE,
                ioOpFailedEvents.single().level,
                "Client-disconnect IOException must be logged at TRACE to avoid noise in DEBUG logs"
            )
        } finally {
            logger.detachAppender(listAppender)
            logger.level = previousLevel
            channel.finishAndReleaseAll()
            callEventGroup.shutdownGracefully()
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

                    withTimeout(5000.milliseconds) {
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

    @Test
    fun `request handler runs on call event group`() = runTestWithRealTime {
        val handlerThread = AtomicReference<Thread>()

        val server = embeddedServer(
            factory = Netty,
            rootConfig = serverConfig {
                module {
                    routing {
                        get("/") {
                            handlerThread.set(Thread.currentThread())
                            call.respondText("ok")
                        }
                    }
                }
            },
            configure = {
                connector { port = 0 }
                // This issue is not relevant if call and worker groups are shared
                shareWorkGroup = false
            }
        )
        server.startSuspend(wait = false)

        try {
            val connector = server.engine.resolvedConnectors().first()
            HttpClient(CIO).use { it.get("http://${connector.host}:${connector.port}/") }

            // Ugly, but we'd like to access the call event group somehow
            val callEventGroup = NettyApplicationEngine::class.java
                .getDeclaredMethod("getCallEventGroup")
                .apply { isAccessible = true }
                .invoke(server.engine) as EventLoopGroup

            val thread = handlerThread.get()
            assertTrue(
                callEventGroup.any { it.inEventLoop(thread) },
                "Handler ran on '${thread.name}', not on any call event group thread"
            )
        } finally {
            server.stopSuspend()
        }
    }

    @Test
    fun `no messages are discarded from the netty pipeline for HTTP1`() = noDiscardedMessagesTest(enableH2c = false) {
        val connector = engine.resolvedConnectors().first()
        val sendUrl = "http://${connector.host}:${connector.port}/send"
        HttpClient(CIO).use { client ->
            repeat(5) { i ->
                client.post(sendUrl) {
                    contentType(ContentType.Application.Json)
                    setBody("{\"foo\":\"bar-$i\"}")
                }.bodyAsText()
            }
        }
    }

    @Test
    fun `no messages are discarded from the netty pipeline for HTTP2`() = noDiscardedMessagesTest(enableH2c = true) {
        val connector = engine.resolvedConnectors().first()
        SelectorManager().use { selector ->
            aSocket(selector).tcp().connect(connector.host, connector.port).use { socket ->
                val writer = socket.openWriteChannel()
                val reader = socket.openReadChannel()

                // HTTP/2 prior-knowledge: send connection preface and an initial SETTINGS
                writer.writeStringUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n")
                writer.writeFully(http2SettingsFrameBytes(ack = false))
                writer.flush()

                // Drain initial server frames (SETTINGS and SETTINGS-ACK)
                readHttp2FrameRaw(reader)
                readHttp2FrameRaw(reader)
                writer.writeFully(http2SettingsFrameBytes(ack = true))
                writer.flush()

                repeat(5) { i ->
                    val streamId = 1 + i * 2
                    writer.writeFully(http2GetHeadersFrameBytes(streamId, "/ping"))
                    writer.flush()

                    // Read response frames until END_STREAM on this stream
                    var endStream = false
                    while (!endStream) {
                        val frame = readHttp2FrameRaw(reader)
                        if (frame.streamId == streamId &&
                            (frame.flags.toInt() and 0x1) != 0
                        ) {
                            endStream = true
                        }
                    }
                }
            }
        }
    }

    private fun noDiscardedMessagesTest(
        enableH2c: Boolean,
        block: suspend EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>.() -> Unit,
    ) = runTestWithRealTime {
        val pipelineLogger = LoggerFactory.getLogger("io.netty.channel.DefaultChannelPipeline") as Logger
        val previousLevel = pipelineLogger.level
        val logEvents = ListAppender<ILoggingEvent>().apply {
            context = pipelineLogger.loggerContext
            start()
        }
        pipelineLogger.level = Level.DEBUG
        pipelineLogger.addAppender(logEvents)

        val server = embeddedServer(
            factory = Netty,
            rootConfig = serverConfig {
                module {
                    routing {
                        post("/send") {
                            call.respondText(call.receive())
                        }
                        get("/ping") {
                            call.respondText("pong")
                        }
                    }
                }
            },
            configure = {
                connector { port = 0 }
                this.enableH2c = enableH2c
            }
        )
        server.startSuspend()
        try {
            server.block()

            val discarded = logEvents.list.filter {
                it.message.orEmpty().contains("Discarded inbound message")
            }

            assertTrue(
                discarded.isEmpty(),
                "Netty pipeline discarded ${discarded.size} inbound messages." +
                    "First message: ${discarded.firstOrNull()?.formattedMessage}"
            )
        } finally {
            server.stopSuspend(0L, 0L)
            pipelineLogger.detachAppender(logEvents)
            logEvents.stop()
            pipelineLogger.level = previousLevel
        }
    }

    private class Http2RawFrame(
        val frameType: Byte,
        val flags: Byte,
        val streamId: Int,
        val payload: ByteArray,
    )

    private suspend fun readHttp2FrameRaw(channel: ByteReadChannel): Http2RawFrame {
        val header = channel.readByteArray(9)
        val payloadLength = ((header[0].toInt() and 0xFF) shl 16) or
            ((header[1].toInt() and 0xFF) shl 8) or
            (header[2].toInt() and 0xFF)
        val frameType = header[3]
        val flags = header[4]
        val streamId = ((header[5].toInt() and 0x7F) shl 24) or
            ((header[6].toInt() and 0xFF) shl 16) or
            ((header[7].toInt() and 0xFF) shl 8) or
            (header[8].toInt() and 0xFF)
        val payload = channel.readByteArray(payloadLength)
        return Http2RawFrame(frameType, flags, streamId, payload)
    }

    private fun http2SettingsFrameBytes(ack: Boolean): ByteArray =
        buildHttp2Frame(type = 0x4, flags = if (ack) 0x1 else 0x0, streamId = 0, payload = ByteArray(0))

    private fun http2GetHeadersFrameBytes(streamId: Int, path: String): ByteArray {
        // Encode HEADERS payload using Netty's HPACK encoder via DefaultHttp2HeadersEncoder
        val headers = io.netty.handler.codec.http2.DefaultHttp2Headers().also {
            it.method("GET")
            it.path(path)
            it.scheme("http")
        }
        val encoded = io.netty.buffer.Unpooled.buffer()
        io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder().encodeHeaders(streamId, headers, encoded)
        val payload = ByteArray(encoded.readableBytes())
        encoded.readBytes(payload)
        // END_HEADERS (0x4) | END_STREAM (0x1)
        return buildHttp2Frame(type = 0x1, flags = 0x5, streamId = streamId, payload = payload)
    }

    private fun buildHttp2Frame(type: Int, flags: Int, streamId: Int, payload: ByteArray): ByteArray {
        val length = payload.size
        val buf = ByteArray(9 + length)
        buf[0] = ((length shr 16) and 0xFF).toByte()
        buf[1] = ((length shr 8) and 0xFF).toByte()
        buf[2] = (length and 0xFF).toByte()
        buf[3] = (type and 0xFF).toByte()
        buf[4] = (flags and 0xFF).toByte()
        buf[5] = ((streamId shr 24) and 0x7F).toByte()
        buf[6] = ((streamId shr 16) and 0xFF).toByte()
        buf[7] = ((streamId shr 8) and 0xFF).toByte()
        buf[8] = (streamId and 0xFF).toByte()
        payload.copyInto(buf, destinationOffset = 9)
        return buf
    }
}
