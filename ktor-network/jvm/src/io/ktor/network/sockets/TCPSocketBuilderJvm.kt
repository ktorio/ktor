/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.net.*

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public suspend fun TcpSocketBuilder.connect(
    remoteAddress: SocketAddress,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket = connect(remoteAddress, configure)
