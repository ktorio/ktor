/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*

@OptIn(ExperimentalKtorApi::class)
internal expect suspend fun udpConnectBufferReceiver(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramBufferSocket
