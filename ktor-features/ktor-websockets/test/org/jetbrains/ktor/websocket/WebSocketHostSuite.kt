package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.application.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.io.*
import java.net.*
import java.nio.*
import java.time.*
import java.util.*
import kotlin.test.*

abstract class WebSocketHostSuite : HostTestBase() {
//    @Test
    @Test(timeout = 5000L)
    fun testWebSocketGenericSequence() {
        val collected = ArrayList<String>()

        createAndStartServer(port) {
            webSocket("/") {
                handle { frame ->
                    if (frame is Frame.Text) {
                        collected.add(frame.readText())
                    }
                }
            }
        }

        Socket("localhost", port).use { socket ->
            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            socket.outputStream.apply {
                // text message with content "Hello"
                writeHex("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
                flush()

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertEquals("880203e8", hex(socket.inputStream.readBytes(4)).takeLast(8), "Server should reply us close frame")
        }

        assertEquals(listOf("Hello"), collected)
    }

        @Test
//    @Test(timeout = 5000L)
    fun testWebSocketPingPong() {
        createAndStartServer(port) {
            webSocket("/") {
                pingInterval = Duration.ofMillis(50)
            }
        }

        Socket("localhost", port).use { socket ->
            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            socket.inputStream.parseStatus()
            socket.inputStream.parseHeaders()

            for (i in 1..5) {
                val frame = socket.inputStream.readFrame()
                assertEquals(FrameType.PING, frame.frameType)
                assertEquals(true, frame.fin)
                assertTrue { frame.buffer.hasRemaining() }
            }

            socket.outputStream.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertEquals("880203e8", hex(socket.inputStream.readBytes(4)).takeLast(8), "Server should reply us close frame")
        }
    }

    private fun OutputStream.writeHex(hex: String) = write(fromHexDump(hex))

    private fun fromHexDump(hex: String) = hex(hex.replace("0x", "").replace("\\s+".toRegex(), ""))

    private fun InputStream.readFrame(): Frame {
        val opcodeAndFin = readOrFail()
        val lenAndMask = readOrFail()

        val frameType = FrameType.byOpcode[opcodeAndFin and 0x0f] ?: throw IllegalStateException("Wrong opcode ${opcodeAndFin and 0x0f}")
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
        val statusCodeString = statusCodeAndMessage.takeWhile { it.isDigit() }
        val message = statusCodeAndMessage.removePrefix(statusCodeString).trimStart()

        return HttpStatusCode(statusCodeString.toInt(), message)
    }

    private fun InputStream.parseHeaders(): ValuesMap {
        val builder = ValuesMapBuilder(caseInsensitiveKey = true)

        while (true) {
            val line = readLineISOCrLf()
            if (line.isEmpty()) {
                return builder.build()
            }

            val (name, value) = line.split(":").map { it.trim() }
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
            throw IOException("EOF")
        }
        return rc
    }
    private fun InputStream.readShortBE() = (readOrFail() shl 8) or readOrFail()
    private fun InputStream.readLongBE() = (readOrFail().toLong() shl 24) or (readOrFail().toLong() shl 16) or (readOrFail().toLong() shl 8) or readOrFail().toLong()
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
