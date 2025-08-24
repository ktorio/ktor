/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.cio.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.testing.suites.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder
import io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt
import io.netty.handler.codec.http2.Http2Flags
import io.netty.handler.codec.http2.Http2FrameTypes
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class NettyCompressionTest : CompressionTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyContentTest : ContentTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyHttpServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttpServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.tcpKeepAlive = true
    }
}

class NettyHttp2ServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttp2ServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyDisabledHttp2Test :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp2 = false
    }

    @Test
    fun testRequestWithDisabledHttp2() = runTest {
        createAndStartServer {
            application.routing {
                get("/") {
                    call.respondText("Hello, world")
                }
            }
        }

        withUrl("/") {
            assertEquals("Hello, world", bodyAsText())
            assertEquals(HttpProtocolVersion.HTTP_1_1, version)
        }
    }
}

class NettySustainabilityTest : SustainabilityTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }

    @Test
    fun testRawWebSocketFreeze() = runTest {
        createAndStartServer {
            application.install(WebSockets)
            webSocket("/ws") {
                repeat(10) {
                    send(Frame.Text("hi"))
                }
            }
        }

        val client = HttpClient(CIO) {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        var count = 0

        client.wsRaw(path = "/ws", port = port) {
            incoming.consumeAsFlow().collect { count++ }
        }

        assertEquals(11, count)
    }
}

class NettyConfigTest : ConfigTestSuite(Netty)

class NettyConnectionTest : ConnectionTestSuite(Netty)

class NettyClientCertTest : ClientCertTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyServerPluginsTest : ServerPluginsTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}

class NettyHooksTest : HooksTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyH2cEnabledTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = false
        enableHttp2 = true
        port = 8994
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableH2c = true
    }

    class Http2Frame(
        val frameType: Byte,
        val flags: Http2Flags,
        val streamId: Int,
        val payload: ByteArray,
    )

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
        private const val PATH = "/"
        private const val BODY = "Hello world"
        private const val STREAM_ID = 3
    }

    @Test
    fun testConnectionUpgradeH2cRequest() = runTest {
        h2cTest { writer, reader ->
            writer.writeStringUtf8("GET $PATH HTTP/1.1\r\n")
            writer.writeStringUtf8("Host: ${TEST_SERVER_HOST}\r\n")
            writer.writeStringUtf8("Connection: Upgrade, HTTP2-Settings\r\n")
            writer.writeStringUtf8("Upgrade: h2c\r\n")
            writer.writeStringUtf8("HTTP2-Settings: AAMAAABkAAQCAAAAAAIAAAAA\r\n")
            writer.writeStringUtf8("\r\n")
            writer.flush()

            val buffer = ByteArray(1024)
            val length = reader.readAvailable(buffer)
            val response = buffer.decodeToString(0, 0 + length)

            assertTrue(response.contains("HTTP/1.1 101 Switching Protocols"))
            assertTrue(response.contains("connection: upgrade"))
            assertTrue(response.contains("upgrade: h2c"))
        }
    }

    @Test
    fun testSendH2cRequestWithConnectionPreface() = runTest {
        h2cTest { writer, reader ->
            // send connection preset
            writer.writeHttp2ConnectionPreface()

            // send settings frame
            writer.writeByteArray(http2SettingsFrame(ack = false))
            writer.flush()

            // read server settings
            val http2ServerSettingsFrame = reader.readHttp2Frame()
            assertEquals(Http2FrameTypes.SETTINGS, http2ServerSettingsFrame.frameType)

            // read server ack
            val http2ServerAckFrame = reader.readHttp2Frame()
            assertEquals(Http2FrameTypes.SETTINGS, http2ServerAckFrame.frameType)
            assertTrue(http2ServerAckFrame.flags.ack())

            // send settings ack frame
            writer.writeByteArray(http2SettingsFrame(ack = true))
            writer.flush()

            // send headers frame
            writer.writeByteArray(http2HeadersFrame())
            writer.flush()

            reader.readHeaderFrame()

            reader.readDataFrame()
        }
    }

    private suspend fun ByteWriteChannel.writeHttp2ConnectionPreface() {
        writeStringUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n")
        flush()
    }

    private suspend fun ByteReadChannel.readHttp2Frame(): Http2Frame {
        val dataHeaderBuf = Unpooled.wrappedBuffer(readByteArray(9))

        val payloadLength = dataHeaderBuf.readUnsignedMedium()
        val frameType = dataHeaderBuf.readByte()
        val flags = Http2Flags(dataHeaderBuf.readUnsignedByte())
        val streamId = readUnsignedInt(dataHeaderBuf)
        val payload = readByteArray(payloadLength)

        return Http2Frame(
            frameType = frameType,
            flags = flags,
            streamId = streamId,
            payload = payload
        )
    }

    private suspend fun ByteReadChannel.readHeaderFrame() {
        val http2Frame = readHttp2Frame()

        val payloadBuff = Unpooled.wrappedBuffer(http2Frame.payload)
        val decoder = DefaultHttp2HeadersDecoder(true)
        val decodedHeaders = decoder.decodeHeaders(STREAM_ID, payloadBuff)

        assertEquals(Http2FrameTypes.HEADERS, http2Frame.frameType)
        assertEquals(STREAM_ID, http2Frame.streamId)
        assertTrue(http2Frame.flags.endOfHeaders())
        assertEquals(decodedHeaders.status(), HttpResponseStatus.OK.codeAsText())
    }

    private suspend fun ByteReadChannel.readDataFrame() {
        val http2Frame = readHttp2Frame()

        val data = String(http2Frame.payload, Charsets.UTF_8)

        assertEquals(Http2FrameTypes.DATA, http2Frame.frameType)
        assertEquals(STREAM_ID, http2Frame.streamId)
        assertTrue(http2Frame.flags.endOfStream())
        assertEquals(BODY, data)
    }


    private fun http2HeadersFrame(): ByteArray {
        val headers = DefaultHttp2Headers().also {
            it.method("GET")
            it.path("/")
            it.scheme("http")
        }

        val encodedHeaders = Unpooled.buffer()
        val encoder = DefaultHttp2HeadersEncoder()
        encoder.encodeHeaders(STREAM_ID, headers, encodedHeaders)

        return http2Frame(
            payload = encodedHeaders,
            type = Http2FrameTypes.HEADERS,
            flags = Http2Flags()
                .endOfHeaders(true)
                .endOfStream(true),
            streamId = STREAM_ID
        )
    }

    private fun http2SettingsFrame(ack: Boolean) = http2Frame(
        payload = null,
        type = Http2FrameTypes.SETTINGS,
        flags = Http2Flags().ack(ack),
        streamId = 0
    )

    private fun http2Frame(payload: ByteBuf?, type: Byte, flags: Http2Flags, streamId: Int): ByteArray {
        val buf = Unpooled.buffer()

        val payloadLength = payload?.readableBytes() ?: 0

        buf.writeMedium(payloadLength)
        buf.writeByte(type.toInt())
        buf.writeByte(flags.value().toInt())
        buf.writeInt(streamId)
        payload?.let {
            buf.writeBytes(it)
        }

        val frame = ByteArray(buf.readableBytes())
        buf.readBytes(frame)

        return frame
    }

    private fun h2cTest(block: suspend (ByteWriteChannel, ByteReadChannel) -> Unit) = runTest {
        val server = createServer {
            routing {
                get(PATH) {
                    call.respondText(BODY)
                }
            }
        }
        server.start(wait = false)

        SelectorManager().use {
            aSocket(it).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                val writeChannel = socket.openWriteChannel()
                val readChannel = socket.openReadChannel()
                block(writeChannel, readChannel)
            }
        }

        server.stop()
    }
}
