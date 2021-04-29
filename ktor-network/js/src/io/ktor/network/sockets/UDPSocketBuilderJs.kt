/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.NetworkAddress

internal actual fun UDPSocketBuilder.Companion.connectUDP(
    selector: SelectorManager,
    remoteAddress: NetworkAddress,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket {
    error("UDP sockets are unsupported on JS platform")
}

internal actual fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket {
    error("UDP sockets are unsupported on JS platform")
}
