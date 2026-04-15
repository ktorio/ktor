/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.netty.http.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.net.*
import kotlin.coroutines.*

internal class NettyHttp3ApplicationRequest(
    call: PipelineCall,
    coroutineContext: CoroutineContext,
    context: ChannelHandlerContext,
    val nettyHeaders: Http3Headers,
    val contentByteChannel: ByteChannel = ByteChannel()
) : NettyApplicationRequest(
    call,
    coroutineContext,
    context,
    contentByteChannel,
    nettyHeaders.path()?.toString() ?: "/",
    keepAlive = false
) {

    override val engineHeaders: Headers by lazy {
        Headers.build {
            nettyHeaders.forEach { (name, value) ->
                if (name.isNotEmpty() && name[0] != ':') {
                    append(
                        name.toString(),
                        value.toString()
                    )
                }
            }
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    val contentActor = actor<Http3DataFrame>(
        Dispatchers.Unconfined,
        kotlinx.coroutines.channels.Channel.UNLIMITED
    ) {
        http3frameLoop(contentByteChannel)
    }

    override val local = HttpMultiplexedConnectionPoint(
        pseudoMethod = nettyHeaders.method(),
        pseudoScheme = nettyHeaders.scheme(),
        pseudoAuthority = nettyHeaders.authority(),
        pseudoPath = nettyHeaders.path(),
        localNetworkAddress = context.channel().localAddress() as? InetSocketAddress,
        remoteNetworkAddress = context.channel().remoteAddress() as? InetSocketAddress,
        httpVersion = "HTTP/3",
    )

    override val cookies: RequestCookies = NettyApplicationRequestCookies(this)
}
