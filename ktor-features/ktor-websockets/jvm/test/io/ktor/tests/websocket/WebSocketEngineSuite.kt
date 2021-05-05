/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.FrameType
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.*
import org.junit.rules.*
import java.io.*
import java.net.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.test.*
import kotlin.test.Ignore
import kotlin.test.Test

@OptIn(WebSocketInternalAPI::class, ExperimentalCoroutinesApi::class)
abstract class WebSocketEngineSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {
    @get:Rule
    val errors = ErrorCollector()

    override fun features(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(WebSockets)
        super.features(application, routingConfigurer)
    }

    @Test
    fun testWebSocketDisconnectDuringConsuming() {
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
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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
    fun testWebSocketDisconnectDuringSending() = runBlocking {
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

        val result = async {
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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
    fun testWebSocketDisconnectDuringDowntime() {
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
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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
    fun testRawWebSocketDisconnectDuringConsuming() {
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
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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

    @Test
    fun testRawWebSocketDisconnectDuringSending() = runBlocking {
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
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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

    @Ignore("For now we assume that without any network interactions the socket will remain open.")
    @Test
    fun testRawWebSocketDisconnectDuringDowntime() {
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
            socket {
                negotiateHttpWebSocket()
                shutdownInput()
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
    fun testWebSocketGenericSequence() {
        val collected = LinkedBlockingQueue<String>()

        val engine = createAndStartServer {
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.add(frame.readText())
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.addError(t)
                }
            }
        }

        socket {
            negotiateHttpWebSocket()

            outputStream.apply {
                // text message with content "Hello"
                writeHex("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
                flush()

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        assertEquals("Hello", collected.take())
    }

    @Test
    fun testWebSocketPingPong() {
        createAndStartServer {
            webSocket("/") {
                timeout = Duration.ofSeconds(120)
                pingInterval = Duration.ofMillis(50)

                try {
                    incoming.consumeEach {
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.addError(t)
                }
            }
        }

        socket {
            negotiateHttpWebSocket()

            for (i in 1..5) {
                val frame = inputStream.readFrame()

                assertEquals(FrameType.PING, frame.frameType)
                assertEquals(true, frame.fin)
                assertTrue { frame.buffer.hasRemaining() }

                Serializer().apply {
                    enqueue(Frame.Pong(frame.buffer.copy()))
                    val buffer = ByteArray(1024)
                    val bb = ByteBuffer.wrap(buffer)
                    serialize(bb)
                    bb.flip()

                    getOutputStream().write(buffer, 0, bb.remaining())
                    getOutputStream().flush()
                }
            }

            outputStream.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testReceiveMessages() {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }
        val bytes = template.toByteArray()

        val collected = LinkedBlockingQueue<String>()

        createAndStartServer {
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.add(frame.readText())
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.addError(t)
                    collected.put(t.toString())
                }
            }
        }

        socket {
            negotiateHttpWebSocket()

            outputStream.apply {
                for (i in 1..count) {
                    writeHex("0x81")
                    write(i)
                    write(bytes, 0, i)
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
            assertEquals(expected, collected.take())
        }

        assertNull(collected.poll())
    }

    @Test
    fun testProduceMessages() {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }

        createAndStartServer {
            webSocket("/") {
                for (i in 1..count) {
                    send(Frame.Text(template.substring(0, i)))
                }
            }
        }

        socket {
            negotiateHttpWebSocket()

            getInputStream().apply {
                for (i in 1..count) {
                    val f = readFrame()
                    assertEquals(FrameType.TEXT, f.frameType)
                    assertEquals(template.substring(0, i), f.buffer.decodeString(Charsets.ISO_8859_1))
                }
            }

            outputStream.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testBigFrame() {
        val content = ByteArray(20 * 1024 * 1024)
        Random().nextBytes(content)

        val sendBuffer = ByteBuffer.allocate(content.size + 100)

        Serializer().apply {
            enqueue(Frame.Binary(true, ByteBuffer.wrap(content)))
            serialize(sendBuffer)

            sendBuffer.flip()
        }

        createAndStartServer {
            webSocket("/") {
                val f = incoming.receive()

                val copied = f.copy()
                outgoing.send(copied)

                flush()
            }
        }

        socket {
            negotiateHttpWebSocket()

            getOutputStream().apply {
                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            getInputStream().apply {
                val frame = readFrame()

                assertEquals(FrameType.BINARY, frame.frameType)
                assertEquals(content.size, frame.buffer.remaining())

                val bytes = ByteArray(content.size)
                frame.buffer.get(bytes)

                assertTrue { bytes.contentEquals(content) }
            }

            getOutputStream().apply {
                Serializer().apply {
                    enqueue(Frame.Close())
                    sendBuffer.clear()
                    serialize(sendBuffer)
                    sendBuffer.flip()
                }

                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testALotOfFrames() {
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
                    errors.addError(t)
                }
            }
        }

        socket {
            negotiateHttpWebSocket()

            val sendBuffer = ByteBuffer.allocate(64)
            outputStream.apply {
                for (i in 1L..expectedCount) {
                    sendBuffer.clear()

                    Serializer().apply {
                        enqueue(Frame.Text(true, ByteBuffer.wrap(i.toString().toByteArray())))
                        serialize(sendBuffer)

                        sendBuffer.flip()
                    }

                    write(sendBuffer.array(), 0, sendBuffer.remaining())
                }

                sendBuffer.clear()
                Serializer().apply {
                    enqueue(Frame.Close())
                    sendBuffer.clear()
                    serialize(sendBuffer)
                    sendBuffer.flip()
                }

                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testServerClosingFirst() {
        createAndStartServer {
            webSocket("/") {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "test"))
            }
        }

        socket {
            negotiateHttpWebSocket()

            // it should be close frame immediately
            assertCloseFrame(CloseReason.Codes.TRY_AGAIN_LATER.code, replyCloseFrame = false)

            // we should be able to write close frame back
            val sendBuffer = ByteBuffer.allocate(64)
            outputStream.apply {
                sendBuffer.clear()
                Serializer().apply {
                    enqueue(Frame.Close())
                    sendBuffer.clear()
                    serialize(sendBuffer)
                    sendBuffer.flip()
                }

                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }
        }
    }

    @Test
    open fun testClientClosingFirst() {
        val deferred = CompletableDeferred<Unit>()

        createAndStartServer {
            webSocket("/") {
                try {
                    assertNull(incoming.receiveOrNull(), "Incoming channel should be closed")
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

        socket {
            negotiateHttpWebSocket()

            val serializer = Serializer()
            val buffer = ByteBuffer.allocate(8192)
            serializer.enqueue(Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "Completed.")))
            serializer.serialize(buffer)

            getOutputStream().write(buffer.array(), buffer.arrayOffset(), buffer.position())
            getOutputStream().flush()

            val reply = getInputStream().readFrame() as Frame.Close
            val reason = reply.readReason()
            assertNotNull(reason)

            runBlocking {
                deferred.await()
            }
        }
    }

    private fun Socket.negotiateHttpWebSocket() {
        // send upgrade request
        outputStream.apply {
            write(
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
                ).toByteArray()
            )
            write("\r\n\r\n".toByteArray())
            flush()
        }

        val status = inputStream.parseStatus()
        assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

        val headers = inputStream.parseHeaders()
        assertEquals("Upgrade", headers[HttpHeaders.Connection])
        assertEquals("websocket", headers[HttpHeaders.Upgrade])
    }

    private fun Socket.assertCloseFrame(
        closeCode: Short = CloseReason.Codes.NORMAL.code,
        replyCloseFrame: Boolean = true
    ) {
        loop@
        while (true) {
            val frame = getInputStream().readFrame()

            when (frame) {
                is Frame.Ping -> continue@loop
                is Frame.Close -> {
                    assertEquals(closeCode, frame.readReason()?.code)
                    if (replyCloseFrame) close()
                    break@loop
                }
                else -> fail("Unexpected frame $frame: \n${hex(frame.buffer.moveToByteArray())}")
            }
        }
    }

    private fun OutputStream.writeHex(hex: String) = write(fromHexDump(hex))

    private fun fromHexDump(hex: String) = hex(hex.replace("0x", "").replace("\\s+".toRegex(), ""))

    private fun InputStream.readFrame(): Frame {
        val opcodeAndFin = readOrFail()
        val lenAndMask = readOrFail()

        val frameType = FrameType[opcodeAndFin and 0x0f]
            ?: throw IllegalStateException("Wrong opcode ${opcodeAndFin and 0x0f}")

        val fin = (opcodeAndFin and 0x80) != 0
        val len1 = lenAndMask and 0x7f
        val mask = (lenAndMask and 0x80) != 0

        assertFalse { mask } // we are not going to use masking in these tests

        val length = when (len1) {
            126 -> readShortBE().toLong()
            127 -> readLongBE()
            else -> len1.toLong()
        }

        assertTrue { len1 < 100000 } // in tests we are not going to use bigger frames
        // so if we fail here it is likely we have encoded frame wrong or stream is broken

        val bytes = readFully(length.toInt())
        return Frame.byType(fin, frameType, ByteBuffer.wrap(bytes))
    }

    private fun InputStream.parseStatus(): HttpStatusCode {
        val line = readLineISOCrLf()

        assertTrue(line.startsWith("HTTP/1.1"), "status line should start with HTTP version, actual content: $line")

        val statusCodeAndMessage = line.removePrefix("HTTP/1.1").trimStart()
        val statusCodeString = statusCodeAndMessage.takeWhile(Char::isDigit)
        val message = statusCodeAndMessage.removePrefix(statusCodeString).trimStart()

        return HttpStatusCode(statusCodeString.toInt(), message)
    }

    private fun InputStream.parseHeaders(): Headers {
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

    private fun InputStream.readLineISOCrLf(): String {
        val sb = StringBuilder(256)

        while (true) {
            val rc = read()
            if (rc == -1 || rc == 0x0a) {
                return sb.toString()
            } else if (rc == 0x0d) {
            } else {
                sb.append(rc.toChar())
            }
        }
    }

    private fun InputStream.readOrFail(): Int {
        val rc = read()
        if (rc == -1) {
            throw EOFException()
        }
        return rc
    }

    private fun InputStream.readShortBE() = (readOrFail() shl 8) or readOrFail()
    private fun InputStream.readLongBE() = (readOrFail().toLong() shl 56) or
        (readOrFail().toLong() shl 48) or
        (readOrFail().toLong() shl 40) or
        (readOrFail().toLong() shl 32) or
        (readOrFail().toLong() shl 24) or
        (readOrFail().toLong() shl 16) or
        (readOrFail().toLong() shl 8) or
        readOrFail().toLong()

    private fun InputStream.readFully(size: Int): ByteArray {
        val array = ByteArray(size)
        var wasRead = 0

        while (wasRead < size) {
            val rc = read(array, wasRead, size - wasRead)
            if (rc == -1) {
                throw IOException("Unexpected EOF")
            }
            wasRead += rc
        }

        return array
    }
}
