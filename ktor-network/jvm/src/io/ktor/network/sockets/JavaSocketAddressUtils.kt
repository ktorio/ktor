/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

public fun SocketAddress.toJavaAddress(): java.net.SocketAddress {
    // Do not read the hostname here because that may trigger a name service reverse lookup.
    return address
}

internal fun java.net.SocketAddress.toSocketAddress(): SocketAddress {
    return when {
        this is java.net.InetSocketAddress -> InetSocketAddress(this)
        this.javaClass.name == UNIX_DOMAIN_SOCKET_ADDRESS_CLASS -> UnixSocketAddress(this)
        else -> error("Unknown socket address type")
    }
}
