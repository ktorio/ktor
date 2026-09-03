/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.netty.http3.HmacQuicTokenHandler
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.quic.QuicTokenHandler
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3RetryTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    private val tokenHandler = CountingQuicTokenHandler(HmacQuicTokenHandler())

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 { quicTokenHandler = tokenHandler }
    }

    @Test
    fun `a handshake with address validation issues and accepts a Retry token`() = runTest {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        withHttp3Client(sslPort) { connection ->
            assertEquals("pong", connection.request(path = "/ping").bodyText)
        }

        assertTrue(
            tokenHandler.tokensWritten.get() >= 1,
            "the server must answer the first Initial packet with a Retry token"
        )
        assertTrue(
            tokenHandler.tokensValidated.get() >= 1,
            "the client's retried handshake must present a token the server then validates"
        )
        assertEquals(
            0,
            tokenHandler.tokensRejected.get(),
            "the server must accept the token it issued itself"
        )
    }

    @Test
    fun `every fresh connection completes its own address validation`() = runTest {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        repeat(3) {
            withHttp3Client(sslPort) { connection ->
                assertEquals("pong", connection.request(path = "/ping").bodyText)
            }
        }

        assertTrue(
            tokenHandler.tokensWritten.get() >= 3,
            "each new connection needs its own Retry, but only ${tokenHandler.tokensWritten.get()} were issued"
        )
        assertEquals(0, tokenHandler.tokensRejected.get(), "no self-issued token may be rejected")
    }

    /** Counts what the server does with tokens, delegating the actual crypto to [delegate]. */
    private class CountingQuicTokenHandler(private val delegate: QuicTokenHandler) : QuicTokenHandler {
        val tokensWritten = AtomicInteger()
        val tokensValidated = AtomicInteger()
        val tokensRejected = AtomicInteger()

        override fun writeToken(out: ByteBuf, dcid: ByteBuf, address: InetSocketAddress): Boolean {
            tokensWritten.incrementAndGet()
            return delegate.writeToken(out, dcid, address)
        }

        override fun validateToken(token: ByteBuf, address: InetSocketAddress): Int {
            val offset = delegate.validateToken(token, address)
            if (offset < 0) tokensRejected.incrementAndGet() else tokensValidated.incrementAndGet()
            return offset
        }

        override fun maxTokenLength(): Int = delegate.maxTokenLength()
    }
}

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3NoRetryTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `a handshake completes without address validation`() = runTest {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        withHttp3Client(sslPort) { connection ->
            assertEquals("pong", connection.request(path = "/ping").bodyText)
        }
    }
}
