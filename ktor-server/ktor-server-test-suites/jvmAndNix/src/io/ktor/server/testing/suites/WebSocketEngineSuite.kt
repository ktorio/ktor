/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlin.random.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
public abstract class WebSocketEngineSuite<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {
    private val errors = mutableListOf<Throwable>()
    override val timeout: Duration = 30.seconds

    override fun plugins(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(WebSockets)
        super.plugins(application, routingConfigurer)
    }

    @Test
    public fun testWebSocketDisconnectDuringConsuming(): Unit = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                incoming.consumeEach {}
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        runBlocking {
            withTimeout(5000) {
                closeReasonJob.join()
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
    }

    @Test
    public fun testWebSocketDisconnectDuringSending(): Unit = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                while (true) {
                    send(Frame.Text("a".repeat(2000)))
                    delay(100)
                }
            }
        }

        val result = async(Dispatchers.Unconfined) {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(150)
            }
        }

        runBlocking {
            withTimeout(5000) {
                closeReasonJob.join()
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
    }

    @Test
    public fun testWebSocketDisconnectDuringDowntime(): Unit = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                delay(10000)
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        runBlocking {
            withTimeout(5000) {
                closeReasonJob.join()
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
        delay(5000)
    }

    @Test
    public fun testRawWebSocketDisconnectDuringConsuming(): Unit = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                incoming.consumeEach {}
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(1000)
            }
        }

        runBlocking {
            withTimeout(5000) {
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
    }

    @Ignore // fails process on native
    @Test
    public fun testRawWebSocketDisconnectDuringSending(): Unit = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                while (true) {
                    send(Frame.Text("a".repeat(2000)))
                    delay(100)
                }
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        runBlocking {
            withTimeout(5000) {
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
    }

    @Ignore // For now we assume that without any network interactions the socket will remain open.
    @Test
    public fun testRawWebSocketDisconnectDuringDowntime(): Unit = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                delay(10000)
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(10000)
            }
        }

        runBlocking {
            withTimeout(5000) {
                contextJob.join()
            }
        }

        runBlocking {
            result.await()
        }
    }

    @Test
    public fun testWebSocketGenericSequence(): Unit = runTest {
        val collected = Channel<String>(Channel.UNLIMITED)

        createAndStartServer {
            webSocket("/") {
                try {
                    val frame = incoming.receive()
                    assertIs<Frame.Text>(frame)
                    collected.send(frame.readText())
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                // text message with content "Hello"
                writeHex("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
                flush()

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        assertEquals("Hello", collected.receive())
    }

    @Test
    public fun testWebSocketPingPong(): Unit = runTest {
        createAndStartServer {
            webSocket("/") {
                timeoutMillis = 120.seconds.inWholeMilliseconds
                pingIntervalMillis = 50.milliseconds.inWholeMilliseconds

                try {
                    incoming.consumeEach {
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            for (i in 1..5) {
                val frame = input.readFrame(Long.MAX_VALUE, 0)

                assertEquals(FrameType.PING, frame.frameType)
                assertEquals(true, frame.fin)
                assertTrue(frame.data.isNotEmpty())

                output.writeFrame(Frame.Pong(frame.data), false)
                output.flush()
            }

            output.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    public fun testReceiveMessages(): Unit = runTest {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }
        val bytes = template.toByteArray()

        val collected = Channel<String>(Channel.UNLIMITED)

        createAndStartServer {
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.send(frame.readText())
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                    collected.send(t.toString())
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                for (i in 1..count) {
                    writeHex("0x81")
                    writeByte(i.toByte())
                    writeFully(bytes, 0, i)
                    flush()
                }

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        for (i in 1..count) {
            val expected = template.substring(0, i)
            assertEquals(expected, collected.receive())
        }

        assertNull(collected.tryReceive().getOrNull())
    }

    @Test
    public fun testProduceMessages(): Unit = runTest {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }

        createAndStartServer {
            webSocket("/") {
                for (i in 1..count) {
                    send(Frame.Text(template.substring(0, i)))
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            input.apply {
                for (i in 1..count) {
                    val f = readFrame(Long.MAX_VALUE, 0)
                    assertEquals(FrameType.TEXT, f.frameType)
                    assertEquals(template.substring(0, i), ByteReadPacket(f.data).readText(Charsets.ISO_8859_1))
                }
            }

            output.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    public fun testBigFrame(): Unit = runTest {
        val content = ByteArray(20 * 1024 * 1024)
        Random.nextBytes(content)

        createAndStartServer {
            webSocket("/") {
                val f = incoming.receive()

                val copied = f.copy()
                outgoing.send(copied)

                flush()
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                writeFrame(Frame.Binary(true, content), false)
                flush()
            }

            input.apply {
                val frame = readFrame(Long.MAX_VALUE, 0)

                assertEquals(FrameType.BINARY, frame.frameType)
                assertEquals(content.size, frame.data.size)
                assertContentEquals(content, frame.data)
            }

            output.apply {
                writeFrame(Frame.Close(), false)
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    public fun testALotOfFrames(): Unit = runTest {
        val expectedCount = 100000L

        createAndStartServer {
            webSocket("/") {
                try {
                    var counter = 1L
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val numberRead = frame.readText().toLong()
                            assertEquals(counter, numberRead, "Wrong packet received")

                            counter++
                        }
                    }

                    assertEquals(expectedCount, counter - 1, "Not all frames received")
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                for (i in 1L..expectedCount) {
                    writeFrame(Frame.Text(true, i.toString().toByteArray()), false)
                }
                writeFrame(Frame.Close(), false)
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    public fun testServerClosingFirst(): Unit = runTest {
        createAndStartServer {
            webSocket("/") {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "test"))
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            // it should be close frame immediately
            assertCloseFrame(CloseReason.Codes.TRY_AGAIN_LATER.code, replyCloseFrame = false)

            // we should be able to write close frame back
            output.apply {
                writeFrame(Frame.Close(), false)
                flush()
            }
        }
    }

    @Test
    public open fun testClientClosingFirst(): Unit = runTest {
        val deferred = CompletableDeferred<Unit>()

        createAndStartServer {
            webSocket("/") {
                try {
                    assertNull(incoming.receiveCatching().getOrNull(), "Incoming channel should be closed")
                    assertFailsWith<CancellationException>("Outgoing channel should be closed properly") {
                        repeat(10) {
                            // we need this loop because the outgoing is not closed immediately
                            outgoing.send(Frame.Text("Should not be sent."))
                            delay(100)
                        }
                    }
                } catch (failed: Throwable) {
                    deferred.completeExceptionally(failed)
                } finally {
                    deferred.complete(Unit)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                writeFrame(Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "Completed.")), false)
                flush()
            }

            val reply = input.readFrame(Long.MAX_VALUE, 0) as Frame.Close
            val reason = reply.readReason()
            assertNotNull(reason)

            deferred.await()
        }
    }

    private suspend fun Connection.negotiateHttpWebSocket() {
        // send upgrade request
        output.apply {
            writeFully(
                """
                    GET / HTTP/1.1
                    Host: localhost:$port
                    Upgrade: websocket
                    Connection: Upgrade
                    Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                    Origin: http://localhost:$port
                    Sec-WebSocket-Protocol: chat
                    Sec-WebSocket-Version: 13
                """.trimIndent().replace(
                    "\n",
                    "\r\n"
                ).encodeToByteArray()
            )
            writeFully("\r\n\r\n".encodeToByteArray())
            flush()
        }

        val status = input.parseStatus()
        assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

        val headers = input.parseHeaders()
        assertEquals("Upgrade", headers[HttpHeaders.Connection])
        assertEquals("websocket", headers[HttpHeaders.Upgrade])
    }

    private suspend fun Connection.assertCloseFrame(
        closeCode: Short = CloseReason.Codes.NORMAL.code,
        replyCloseFrame: Boolean = true
    ) {
        loop@
        while (true) when (val frame = input.readFrame(Long.MAX_VALUE, 0)) {
            is Frame.Ping -> continue@loop
            is Frame.Close -> {
                assertEquals(closeCode, frame.readReason()?.code)
                if (replyCloseFrame) socket.close()
                break@loop
            }
            else -> fail("Unexpected frame $frame: \n${hex(frame.data)}")
        }
    }

    private suspend fun ByteWriteChannel.writeHex(hex: String) = writeFully(fromHexDump(hex))

    private fun fromHexDump(hex: String) = hex(hex.replace("0x", "").replace("\\s+".toRegex(), ""))

    //
    private suspend fun ByteReadChannel.parseStatus(): HttpStatusCode {
        val line = readLineISOCrLf()

        assertTrue(line.startsWith("HTTP/1.1"), "status line should start with HTTP version, actual content: $line")

        val statusCodeAndMessage = line.removePrefix("HTTP/1.1").trimStart()
        val statusCodeString = statusCodeAndMessage.takeWhile(Char::isDigit)
        val message = statusCodeAndMessage.removePrefix(statusCodeString).trimStart()

        return HttpStatusCode(statusCodeString.toInt(), message)
    }

    private suspend fun ByteReadChannel.parseHeaders(): Headers {
        val builder = HeadersBuilder()

        while (true) {
            val line = readLineISOCrLf()
            if (line.isEmpty()) {
                return builder.build()
            }

            val (name, value) = line.split(":").map(String::trim)
            builder.append(name, value)
        }
    }

    private suspend fun ByteReadChannel.readLineISOCrLf(): String {
        val sb = StringBuilder(256)

        while (true) when (val rc = readByte().toInt()) {
            -1, 0x0a -> return sb.toString()
            0x0d -> {}
            else -> sb.append(rc.toChar())
        }
    }

    private suspend inline fun useSocket(block: Connection.() -> Unit) {
        SelectorManager().use {
            aSocket(it).tcp().connect("localhost", port) {
                noDelay = true
                socketTimeout = 4.minutes.inWholeMilliseconds
            }.use {
                val connection = it.connection()
                try {
                    block(connection)
                    // for native, output should be closed explicitly
                    connection.output.close()
                } catch (cause: Throwable) {
                    throw cause
                }
            }
        }
    }
}
