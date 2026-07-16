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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression test: the HTTP/3 listener must serve more than one QUIC connection.
 *
 * Before the per-connection Http3ServerConnectionHandler fix, the single (non-@Sharable)
 * handler instance passed to QuicServerCodecBuilder.handler(...) made every QUIC connection
 * after the first fail pipeline initialization, so all subsequent handshakes timed out and
 * the listener could only ever serve one connection.
 */
class NettyHttp3MultipleConnectionsTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        // FreePorts probes with TCP sockets, but HTTP/3 binds UDP on sslPort; on Windows,
        // TCP-free ports often fall into Hyper-V/WSL UDP excluded ranges and fail the bind.
        // Pin ports outside the dynamic range for stability.
        port = 24440
        sslPort = 24441
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    private class H3Conn(
        val group: NioEventLoopGroup,
        val udp: Channel,
        val quic: QuicChannel
    ) {
        fun close() {
            runCatching { quic.close().sync() }
            runCatching { udp.close().sync() }
            runCatching { group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS).sync() }
        }
    }

    private fun openConn(label: String): H3Conn? {
        val group = NioEventLoopGroup(1)
        try {
            val sslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(*Http3.supportedApplicationProtocols())
                .build()
            val codec = Http3.newQuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(30_000, TimeUnit.MILLISECONDS)
                .initialMaxData(10_000_000)
                .initialMaxStreamDataBidirectionalLocal(1_000_000)
                .initialMaxStreamDataBidirectionalRemote(1_000_000)
                .initialMaxStreamsBidirectional(100)
                .build()
            val udp = Bootstrap()
                .group(group)
                .channel(NioDatagramChannel::class.java)
                .handler(codec)
                .bind(0).sync().channel()
            val quic: QuicChannel
            val ms = measureTimeMillis {
                quic = QuicChannel.newBootstrap(udp)
                    .handler(Http3ClientConnectionHandler())
                    .remoteAddress(InetSocketAddress("127.0.0.1", sslPort))
                    .connect().get(5, TimeUnit.SECONDS)
            }
            println("[probe] $label: connected in ${ms}ms")
            return H3Conn(group, udp, quic)
        } catch (ignored: TimeoutException) {
            println("[probe] $label: CONNECT TIMED OUT after 5s")
            group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS)
            return null
        } catch (cause: Throwable) {
            println("[probe] $label: connect failed: ${cause::class.simpleName}: ${cause.message}")
            group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS)
            return null
        }
    }

    private fun get(conn: H3Conn, label: String): String? {
        val responses = LinkedBlockingQueue<String>()
        val handler = object : ChannelInboundHandlerAdapter() {
            val body = StringBuilder()
            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                when (msg) {
                    is Http3HeadersFrame -> Unit

                    is Http3DataFrame -> {
                        body.append(msg.content().toString(Charsets.UTF_8))
                        msg.release()
                    }

                    else -> super.channelRead(ctx, msg)
                }
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                responses.offer(body.toString())
                super.channelInactive(ctx)
            }
        }
        return try {
            val stream = Http3.newRequestStream(conn.quic, handler).sync().getNow()
            val headers = DefaultHttp3Headers().apply {
                method("GET")
                path("/i")
                scheme("https")
                authority("localhost:$sslPort")
            }
            stream.writeAndFlush(DefaultHttp3HeadersFrame(headers)).sync()
            stream.shutdownOutput().sync()
            val body = responses.poll(5, TimeUnit.SECONDS)
            println("[probe] $label: GET /i -> ${body ?: "TIMED OUT"}")
            body
        } catch (cause: Throwable) {
            println("[probe] $label: request failed: ${cause::class.simpleName}: ${cause.message}")
            null
        }
    }

    @Test
    fun `probe multiple QUIC connections to one listener`() = runTest {
        createAndStartServer {
            application.routing {
                get("/i") { call.respondText("ok") }
            }
        }

        val connections = mutableListOf<H3Conn>()
        try {
            val conn1 = assertNotNull(openConn("conn1 (fresh listener)"), "first connection must connect")
            connections += conn1
            assertEquals("ok", get(conn1, "conn1"))

            val conn2 = assertNotNull(
                openConn("conn2 (while conn1 still open)"),
                "a second concurrent QUIC connection must be accepted"
            )
            connections += conn2
            assertEquals("ok", get(conn2, "conn2"))

            conn1.close()
            println("[probe] conn1 closed")

            val conn3 = assertNotNull(
                openConn("conn3 (after conn1 closed)"),
                "a new QUIC connection after a previous one closed must be accepted"
            )
            connections += conn3
            assertEquals("ok", get(conn3, "conn3"))
            println("[probe] done")
        } finally {
            connections.asReversed().forEach { it.close() }
        }
    }
}
