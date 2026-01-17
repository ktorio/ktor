/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Handler for HTTP CONNECT tunnel establishment for HTTPS over HTTP proxy.
 */
internal class ProxyConnectHandler(
    private val targetHost: String,
    private val targetPort: Int,
    private val callContext: CoroutineContext
) : SimpleChannelInboundHandler<HttpObject>() {

    private val connectComplete = CompletableDeferred<Unit>()

    override fun channelActive(ctx: ChannelHandlerContext) {
        // Send CONNECT request to establish tunnel
        val connectRequest = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.CONNECT,
            "$targetHost:$targetPort"
        )
        connectRequest.headers().set(HttpHeaderNames.HOST, "$targetHost:$targetPort")
        connectRequest.headers().set(HttpHeaderNames.PROXY_CONNECTION, "Keep-Alive")

        ctx.writeAndFlush(connectRequest)
        super.channelActive(ctx)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        when (msg) {
            is HttpResponse -> {
                if (msg.status().code() == 200) {
                    // Tunnel established successfully
                    // Remove this handler and the codec from pipeline
                    ctx.pipeline().remove(this)
                    ctx.pipeline().remove("http-codec")
                    connectComplete.complete(Unit)
                } else {
                    connectComplete.completeExceptionally(
                        IllegalStateException("Proxy CONNECT failed: ${msg.status()}")
                    )
                }
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        connectComplete.completeExceptionally(cause)
        ctx.close()
    }

    suspend fun awaitConnect() {
        connectComplete.await()
    }

    companion object {
        fun set(channel: io.netty.channel.Channel, handler: ProxyConnectHandler) {
            channel.attr(ATTRIBUTE_KEY).set(handler)
        }

        fun get(channel: io.netty.channel.Channel): ProxyConnectHandler? {
            return channel.attr(ATTRIBUTE_KEY).get()
        }

        private val ATTRIBUTE_KEY = io.netty.util.AttributeKey.valueOf<ProxyConnectHandler>("ProxyConnectHandler")
    }
}
