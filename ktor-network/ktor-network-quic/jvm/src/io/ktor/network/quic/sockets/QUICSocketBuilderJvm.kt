/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.sockets

import io.ktor.network.quic.streams.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
// import jdk.net.ExtendedSocketOptions

@OptIn(InternalAPI::class)
internal actual fun QUICSocketBuilder.Companion.bindQUIC(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
): BoundQUICSocket {
    val datagramSocket = bindUDPConfigurable(selector, localAddress, options) {
        // https://www.rfc-editor.org/rfc/rfc9000.html#name-datagram-size
        // todo requires java 19 and gradle 7.6
//        setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true)
    }
    return QUICServer(datagramSocket)
}
