/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.*

/**
 * Receives UDP datagrams into caller-provided byte arrays.
 */
@ExperimentalKtorApi
public data class DatagramBufferReceiver(
    public val receiveInto: suspend (ByteArray, Int, Int) -> DatagramReceiveResult,
)

@ExperimentalKtorApi
public data class BoundDatagramBufferSocket(
    public val receiver: DatagramBufferReceiver,
    public val localAddress: () -> SocketAddress,
    public val send: suspend (Datagram) -> Unit,
    public val close: () -> Unit,
)

@ExperimentalKtorApi
public data class ConnectedDatagramBufferSocket(
    public val bound: BoundDatagramBufferSocket,
    public val remoteAddress: () -> SocketAddress
)
