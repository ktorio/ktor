/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

internal actual suspend fun tcpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = error("TCP Sockets are unsupported on wasm-wasi")

internal actual suspend fun tcpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = error("TCP Sockets are unsupported on wasm-wasi")

internal actual suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket = error("UDP sockets are unsupported on wasm-wasi")

internal actual suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket = error("UDP sockets are unsupported on wasm-wasi")
