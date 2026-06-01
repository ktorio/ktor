/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ServerConnectionHandler
import io.netty.handler.codec.quic.QuicSslContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * A [ChannelInitializer] for QUIC/HTTP3 that configures the [DatagramChannel] pipeline
 * with the QUIC server codec and HTTP/3 connection handler.
 */
internal class NettyHttp3ChannelInitializer(
    private val applicationProvider: () -> Application,
    private val enginePipeline: EnginePipeline,
    private val userContext: CoroutineContext,
    private val runningLimit: Int,
    private val quicSslContext: QuicSslContext,
    private val http3Configuration: NettyHttp3Configuration
) : ChannelInitializer<DatagramChannel>() {

    override fun initChannel(ch: DatagramChannel) {
        val application = applicationProvider()

        val streamInitializer = NettyHttp3RequestStreamInitializer(
            enginePipeline,
            application,
            userContext,
            runningLimit
        )

        val quicServerCodec = Http3.newQuicServerCodecBuilder()
            .sslContext(quicSslContext)
            .tokenHandler(http3Configuration.quicTokenHandler)
            .maxIdleTimeout(http3Configuration.quicMaxIdleTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .initialMaxData(http3Configuration.quicInitialMaxData)
            .initialMaxStreamDataBidirectionalLocal(http3Configuration.quicInitialMaxStreamDataBidirectionalLocal)
            .initialMaxStreamDataBidirectionalRemote(http3Configuration.quicInitialMaxStreamDataBidirectionalRemote)
            .initialMaxStreamsBidirectional(http3Configuration.quicInitialMaxStreamsBidirectional)
            .apply(http3Configuration.configureQuicServerCodec)
            .handler(Http3ServerConnectionHandler(streamInitializer))
            .build()

        ch.pipeline().addLast(quicServerCodec)
    }
}
