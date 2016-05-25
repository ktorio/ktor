package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class ParserTest {
    @Test
    fun testHello() {
        withTestApplication {
            application.routing {
                webSocket("/echo") {
                    handle { frame ->
                        if (!frame.frameType.controlFrame) {
                            outbound.send(frame.copy())
                            close()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                bodyBytes = hex("""
                    0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f
                """.replace("\\s+".toRegex(), "").replace("0x", ""))
            }.let { call ->
                assertEquals("810548656c6c6f", hex(call.response.byteContent!!))
            }
        }
    }
}
