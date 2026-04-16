/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http3.*
import io.netty.handler.codec.quic.*
import io.netty.util.concurrent.*
import kotlin.coroutines.*

/**
 * A [ChannelInitializer] for QUIC/HTTP3 that configures the [DatagramChannel] pipeline
 * with the QUIC server codec and HTTP/3 connection handler.
 */
internal class NettyHttp3ChannelInitializer(
    private val applicationProvider: () -> Application,
    private val enginePipeline: EnginePipeline,
    private val callEventGroup: EventExecutorGroup,
    private val userContext: CoroutineContext,
    private val runningLimit: Int,
    private val quicSslContext: QuicSslContext,
    private val quicTokenHandler: QuicTokenHandler
) : ChannelInitializer<DatagramChannel>() {

    override fun initChannel(ch: DatagramChannel) {
        val application = applicationProvider()

        val streamInitializer = NettyHttp3RequestStreamInitializer(
            enginePipeline,
            application,
            callEventGroup,
            userContext,
            runningLimit
        )

        val quicServerCodec = Http3.newQuicServerCodecBuilder()
            .sslContext(quicSslContext)
            .tokenHandler(quicTokenHandler)
            .maxIdleTimeout(30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(100)
            .handler(Http3ServerConnectionHandler(streamInitializer))
            .build()

        ch.pipeline().addLast(quicServerCodec)
    }
}
