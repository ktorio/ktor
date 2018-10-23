package io.ktor.tests.websocket

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.FrameType
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*

@UseExperimental(WebSocketInternalAPI::class, ObsoleteCoroutinesApi::class)
class WebSocketTest {
    @get:Rule
    val timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    fun testSingleEcho() {
        withTestApplication {
            application.install(WebSockets)
            application.routing {
                webSocketRaw("/echo") {
                    incoming.consumeEach { frame ->
                        if (!frame.frameType.controlFrame) {
                            send(frame.copy())
                            flush()
                            terminate()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))
                assertEquals("810548656c6c6f", hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testFrameSize() {
        withTestApplication {
            application.install(WebSockets)
            application.routing {
                webSocketRaw("/echo") {
                    outgoing.send(Frame.Text("+".repeat(0xc123)))
                    outgoing.send(Frame.Close())
                }
                webSocketRaw("/receiveSize") {
                    val frame = incoming.receive()
                    val bytes = buildPacket {
                        byteOrder = ByteOrder.BIG_ENDIAN
                        writeInt(frame.buffer.remaining())
                    }

                    outgoing.send(Frame.Binary(true, bytes))
                    outgoing.send(Frame.Close())
                }
            }

            handleWebSocket("/echo") {
                setBody(byteArrayOf())
            }.let { call ->
                assertEquals("817ec123", hex(call.response.byteContent!!.take(4).toByteArray()))
                call.response.awaitWebSocket(Duration.ofSeconds(10))
            }

            handleWebSocket("/receiveSize") {
                setBody(hex("0x81 0x7e 0xcd 0xef".trimHex()) + "+".repeat(0xcdef).toByteArray())
            }.let { call ->
                assertEquals("82040000cdef", hex(call.response.byteContent!!.take(6).toByteArray()))
                call.response.awaitWebSocket(Duration.ofSeconds(10))
            }
        }
    }

    @Test
    fun testMasking() {
        withTestApplication {
            application.install(WebSockets)
            application.routing {
                webSocketRaw("/echo") {
                    masking = true

                    incoming.consumeEach { frame ->
                        if (!frame.frameType.controlFrame) {
                            assertEquals("Hello", frame.buffer.copy().array().toString(Charsets.UTF_8))
                            send(frame.copy())
                            flush()
                            terminate()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x81 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))

                val bb = ByteBuffer.wrap(call.response.byteContent!!)
                assertEquals(11, bb.remaining())

                val parser = FrameParser()
                parser.frame(bb)

                assertTrue { parser.bodyReady }
                assertTrue { parser.mask }
                val key = parser.maskKey!!

                val collector = SimpleFrameCollector()
                collector.start(parser.length.toInt(), bb)

                assertFalse { collector.hasRemaining }

                assertEquals("Hello", collector.take(key).copy().array().toString(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun testSendClose() {
        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/echo") {
                    incoming.consumeEach { }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x88 0x02 0xe8 0x03""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))
                assertEquals("0x88 0x02 0xe8 0x03".trimHex(), hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testParameters() {
        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/{p}") {
                    outgoing.send(Frame.Text(call.parameters["p"] ?: "null"))
                }
            }

            handleWebSocket("/aaa") {}.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))
                val p = FrameParser()
                val bb = ByteBuffer.wrap(call.response.byteContent)
                p.frame(bb)

                assertEquals(FrameType.TEXT, p.frameType)
                assertTrue { p.bodyReady }

                val bytes = ByteArray(p.length.toInt())
                bb.get(bytes)

                assertEquals("aaa", bytes.toString(Charsets.ISO_8859_1))
            }
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

        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/") {
                    val frame = incoming.receive()
                    val copied = frame.copy()
                    outgoing.send(copied)

                    flush()
                }
            }

            handleWebSocket("/") {
                setBody(sendBuffer.array())
            }.let { call ->
                runBlocking {
                    withTimeout(Duration.ofSeconds(10).toMillis()) {
                        val reader = WebSocketReader(
                            call.response.websocketChannel()!!,
                            coroutineContext,
                            Int.MAX_VALUE.toLong()
                        )

                        val frame = reader.incoming.receive()
                        val receivedContent = frame.buffer.moveToByteArray()

                        assertEquals(FrameType.BINARY, frame.frameType)
                        assertEquals(content.size, receivedContent.size)

                        assertTrue { receivedContent.contentEquals(content) }
                    }

                    call.response.awaitWebSocket(Duration.ofSeconds(10))
                }
            }
        }
    }

    @Test
    fun testFragmentation() {
        val sendBuffer = ByteBuffer.allocate(1024)

        Serializer().apply {
            enqueue(Frame.Text(false, ByteBuffer.wrap("ABC".toByteArray())))
            enqueue(Frame.Ping(ByteBuffer.wrap("ping".toByteArray()))) // ping could be interleaved
            enqueue(Frame.Text(false, ByteBuffer.wrap("12".toByteArray())))
            enqueue(Frame.Text(true, ByteBuffer.wrap("3".toByteArray())))
            enqueue(Frame.Close())
            serialize(sendBuffer)

            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets)

            var receivedText: String? = null
            application.routing {
                webSocket("/") {
                    val frame = incoming.receive()

                    if (frame is Frame.Text) {
                        receivedText = frame.readText()
                    } else {
                        fail()
                    }
                }
            }

            handleWebSocket("/") {
                setBody(sendBuffer.array())
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))

                assertEquals("ABC123", receivedText)
            }
        }
    }

    @Test
    fun testConversation() {
        withTestApplication {
            application.install(WebSockets)

            val received = arrayListOf<String>()
            application.routing {
                webSocket("/echo") {
                    try {
                        while (true) {
                            val text = (incoming.receive() as Frame.Text).readText()
                            received += text
                            outgoing.send(Frame.Text(text))
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        // Do nothing!
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }

            handleWebSocketConversation("/echo") { incoming, outgoing ->
                val textMessages = listOf("HELLO", "WORLD")
                for (msg in textMessages) {
                    outgoing.send(Frame.Text(msg))
                    assertEquals(msg, (incoming.receive() as Frame.Text).readText())
                }
                assertEquals(textMessages, received)
            }
        }
    }

    private fun String.trimHex() = replace("\\s+".toRegex(), "").replace("0x", "")
}
