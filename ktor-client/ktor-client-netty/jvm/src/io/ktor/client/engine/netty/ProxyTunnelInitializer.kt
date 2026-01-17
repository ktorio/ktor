/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.netty

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.*
import kotlin.coroutines.*

/**
 * Initializer for establishing HTTP CONNECT tunnel through proxy.
 */
internal class ProxyTunnelInitializer(
    private val targetHost: String,
    private val targetPort: Int,
    private val callContext: CoroutineContext
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()

        // Add HTTP codec for CONNECT request/response
        pipeline.addLast("http-codec", HttpClientCodec())

        // Add proxy connect handler
        val connectHandler = ProxyConnectHandler(targetHost, targetPort, callContext)
        ProxyConnectHandler.set(ch, connectHandler)
        pipeline.addLast("proxy-connect", connectHandler)
    }
}
