/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.sockets.nodejs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*
import io.ktor.network.sockets.nodejs.Socket as NodejsSocket

internal actual suspend fun tcpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = suspendCancellableCoroutine { cont ->
    val socket = nodeNet.createConnection(CreateConnectionOptions(remoteAddress, socketOptions))
    SocketContext(socket, remoteAddress, null).initiate(cont)
}

internal actual suspend fun tcpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = suspendCancellableCoroutine { cont ->
    val server = nodeNet.createServer(CreateServerOptions {})
    ServerSocketContext(server, localAddress, null).initiate(cont)
}

internal actual suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket = error("UDP sockets are unsupported on WASM/JS")

internal actual suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket = error("UDP sockets are unsupported on WASM/JS")
