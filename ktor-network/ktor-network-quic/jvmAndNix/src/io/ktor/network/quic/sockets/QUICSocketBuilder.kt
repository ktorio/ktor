/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.sockets

import io.ktor.network.quic.streams.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*

public class QUICSocketBuilder(
    private val selector: SelectorManager,
    override var options: SocketOptions.QUICSocketOptions
) : Configurable<QUICSocketBuilder, SocketOptions.QUICSocketOptions> {
    @OptIn(InternalAPI::class)
    public fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.QUICSocketOptions.() -> Unit = {}
    ): BoundQUICSocket = bindQUIC(selector, localAddress, options.quic().apply(configure))

    public companion object
}

internal expect fun QUICSocketBuilder.Companion.bindQUIC(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
): BoundQUICSocket
