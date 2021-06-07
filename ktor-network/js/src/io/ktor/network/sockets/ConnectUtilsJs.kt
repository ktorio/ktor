/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.NetworkAddress

internal actual suspend fun connect(
    selector: SelectorManager,
    networkAddress: NetworkAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket {
    error("TCP sockets are unsupported on js platform")
}

internal actual fun bind(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket {
    error("TCP sockets are unsupported on js platform")
}
