/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.sockets

import io.ktor.network.quic.streams.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*

internal actual fun QUICSocketBuilder.Companion.bindQUIC(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
): BoundQUICSocket {
    TODO("QUIC protocol is not supported for native platforms")
}
