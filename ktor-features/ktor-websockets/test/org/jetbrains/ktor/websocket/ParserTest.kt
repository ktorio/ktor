package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class ParserTest {
    @Test
    fun testHello() {
        withTestApplication {
            application.routing {
                webSocket("/echo") {
                    handle { frame ->
                        if (!frame.frameType.controlFrame) {
                            send(frame.copy())
                            close()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                bodyBytes = hex("""
                    0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f
                """.trimHex())
            }.let { call ->
                assertEquals("810548656c6c6f", hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testMasking() {
        withTestApplication {
            application.routing {
                webSocket("/echo") {
                    masking = true

                    handle { frame ->
                        if (!frame.frameType.controlFrame) {
                            assertEquals("Hello", frame.buffer.copy().array().toString(Charsets.UTF_8))
                            send(frame.copy())
                            close()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                bodyBytes = hex("""
                    0x81 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58
                """.trimHex())
            }.let { call ->
                val bb = ByteBuffer.wrap(call.response.byteContent!!)
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
    @Ignore
    fun testPingResponse() {
        withTestApplication {
            application.routing {
                webSocket("/echo") {
                    masking = true
                }
            }

            handleWebSocket("/echo") {
                bodyBytes = hex("""
                    0x89 0x05 0x48 0x65 0x6c 0x6c 0x6f
                """.trimHex())
            }.let { call ->
                assertEquals("0x8a 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58".trimHex(), hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testSendClose() {
        withTestApplication {
            application.routing {
                webSocket("/echo") {
                }
            }

            handleWebSocket("/echo") {
                bodyBytes = hex("""
                    0x88 0x02 0xe8 0x03
                """.trimHex())
            }.let { call ->
                assertEquals("0x88 0x02 0xe8 0x03".trimHex(), hex(call.response.byteContent!!))
            }
        }
    }

    private fun String.trimHex() = replace("\\s+".toRegex(), "").replace("0x", "")
}
