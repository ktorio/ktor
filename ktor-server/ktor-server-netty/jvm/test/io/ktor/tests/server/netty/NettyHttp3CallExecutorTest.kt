/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http3.*
import io.netty.handler.codec.quic.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test: application handler code must not run on the QUIC event loop.
 *
 * Without dispatching calls to an executor pinned from the call event group (as HTTP/1 and
 * HTTP/2 do since KTOR-9542), user handler code runs on the QUIC event loop. All QUIC
 * connections of a connector share a single DatagramChannel driven by one event loop, so any
 * blocking user code (JDBC, file IO) stalls the entire HTTP/3 listener, including QUIC
 * handshakes of unrelated new connections.
 */
class NettyHttp3CallExecutorTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `blocking user code on one HTTP3 connection must not stall other connections`() = runTest {
        val blockEntered = CountDownLatch(1)
        val releaseBlock = CountDownLatch(1)

        createAndStartServer {
            application.routing {
                get("/instant") { call.respondText("ok") }
                get("/block") {
                    blockEntered.countDown()
                    releaseBlock.await() // deliberately blocking user code (e.g. JDBC, file IO)
                    call.respondText("done")
                }
            }
        }

        withHttp3Client { connectionA ->
            // fire GET /block on connection A without awaiting the response
            val pending = Http3ResponseHandler()
            val stream = Http3.newRequestStream(connectionA, pending).sync().getNow()
            stream.writeAndFlush(DefaultHttp3HeadersFrame(requestHeaders("/block"))).sync()
            stream.shutdownOutput().sync()
            assertTrue(blockEntered.await(5, TimeUnit.SECONDS), "server never entered /block")

            // While /block is still parked on releaseBlock above, a brand-new QUIC connection
            // (fresh handshake) must still be served. This only succeeds if the blocking
            // handler runs off the shared QUIC event loop, since all connections of a connector
            // share a single DatagramChannel driven by one event loop.
            withHttp3Client { connectionB ->
                val response = sendHttp3Request(connectionB, "/instant")
                assertEquals("ok", response.body)
            }

            releaseBlock.countDown()
            val blockResponse = pending.responseQueue.poll(5, TimeUnit.SECONDS)
            assertEquals("done", blockResponse?.body)
        }
    }

    // --- HTTP/3 client helpers (mirroring NettyHttp3Test, where they are private) ---

    private data class Http3Response(
        val status: String,
        val headers: Map<String, String>,
        val body: String
    )

    private class Http3ResponseHandler : ChannelInboundHandlerAdapter() {
        val responseQueue = LinkedBlockingQueue<Http3Response>()
        private var status: String = ""
        private var headers: MutableMap<String, String> = mutableMapOf()
        private val bodyParts = mutableListOf<ByteArray>()

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is Http3HeadersFrame -> {
                    val h = msg.headers()
                    status = h.status()?.toString() ?: ""
                    h.forEach { (name, value) ->
                        val nameStr = name.toString()
                        if (!nameStr.startsWith(":")) {
                            headers[nameStr] = value.toString()
                        }
                    }
                }

                is Http3DataFrame -> {
                    val content = msg.content()
                    val bytes = ByteArray(content.readableBytes())
                    content.readBytes(bytes)
                    bodyParts.add(bytes)
                    msg.release()
                }

                else -> super.channelRead(ctx, msg)
            }
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            val body = bodyParts.joinToString("") { String(it, Charsets.UTF_8) }
            responseQueue.offer(Http3Response(status, headers, body))
            status = ""
            headers = mutableMapOf()
            bodyParts.clear()
            super.channelInactive(ctx)
        }
    }

    private fun requestHeaders(path: String): Http3Headers = DefaultHttp3Headers().apply {
        method("GET")
        path(path)
        scheme("https")
        authority("localhost:$sslPort")
    }

    private suspend fun withHttp3Client(block: suspend (QuicChannel) -> Unit) {
        val group = NioEventLoopGroup(1)
        try {
            val quicSslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(*Http3.supportedApplicationProtocols())
                .build()

            val quicClientCodec = Http3.newQuicClientCodecBuilder()
                .sslContext(quicSslContext)
                .maxIdleTimeout(30_000, TimeUnit.MILLISECONDS)
                .initialMaxData(10_000_000)
                .initialMaxStreamDataBidirectionalLocal(1_000_000)
                .initialMaxStreamDataBidirectionalRemote(1_000_000)
                .initialMaxStreamsBidirectional(100)
                .build()

            val udpChannel = Bootstrap()
                .group(group)
                .channel(NioDatagramChannel::class.java)
                .handler(quicClientCodec)
                .bind(0)
                .sync()
                .channel()

            val quicChannel = QuicChannel.newBootstrap(udpChannel)
                .handler(Http3ClientConnectionHandler())
                .remoteAddress(InetSocketAddress("127.0.0.1", sslPort))
                .connect()
                .get(10, TimeUnit.SECONDS)

            try {
                block(quicChannel)
            } finally {
                runCatching { quicChannel.close().sync() }
                runCatching { udpChannel.close().sync() }
            }
        } finally {
            group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS).sync()
        }
    }

    private fun sendHttp3Request(quicChannel: QuicChannel, path: String): Http3Response {
        val responseHandler = Http3ResponseHandler()

        val stream = Http3.newRequestStream(quicChannel, responseHandler).sync().getNow()
        stream.writeAndFlush(DefaultHttp3HeadersFrame(requestHeaders(path))).sync()
        stream.shutdownOutput().sync()

        return responseHandler.responseQueue.poll(10, TimeUnit.SECONDS)
            ?: error("Timed out waiting for HTTP/3 response for $path")
    }
}
