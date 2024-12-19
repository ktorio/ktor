/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

internal expect suspend fun tcpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket

internal expect suspend fun tcpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket

internal expect suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket

internal expect suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket
