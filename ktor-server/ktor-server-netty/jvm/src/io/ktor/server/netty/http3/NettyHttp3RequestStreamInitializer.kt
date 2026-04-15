/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.netty.channel.*
import io.netty.handler.codec.quic.*
import io.netty.util.concurrent.*
import kotlin.coroutines.*

/**
 * Initializer for HTTP/3 request streams. Creates a new [NettyHttp3Handler] for each
 * incoming QUIC stream, since HTTP/3 request stream handlers are not sharable.
 *
 * This extends [ChannelInitializer] rather than [io.netty.handler.codec.http3.Http3RequestStreamInitializer]
 * because [io.netty.handler.codec.http3.Http3ServerConnectionHandler] already sets up the HTTP/3 codec
 * pipeline in its [io.netty.handler.codec.http3.Http3ServerConnectionHandler.initBidirectionalStream] method.
 * Using [io.netty.handler.codec.http3.Http3RequestStreamInitializer] would result in duplicate codec handlers.
 */
internal class NettyHttp3RequestStreamInitializer(
    private val enginePipeline: EnginePipeline,
    private val application: Application,
    private val callEventGroup: EventExecutorGroup,
    private val userCoroutineContext: CoroutineContext,
    private val runningLimit: Int
) : ChannelInitializer<QuicStreamChannel>() {

    override fun initChannel(ch: QuicStreamChannel) {
        ch.pipeline().addLast(
            NettyHttp3Handler(
                enginePipeline,
                application,
                callEventGroup,
                userCoroutineContext,
                runningLimit
            )
        )
    }
}
