/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

private const val UNIX_DOMAIN_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress"

public fun SocketAddress.toJavaAddress(): java.net.SocketAddress {
    return when (this) {
        is InetSocketAddress -> java.net.InetSocketAddress(hostname, port)
        is UnixSocketAddress -> {
            checkSupportForUnixDomainSockets()
            val ofMethod = Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS).getMethod("of", String::class.java)
            ofMethod.invoke(null, path) as java.net.SocketAddress
        }
    }
}

internal fun java.net.SocketAddress.toSocketAddress(): SocketAddress {
    return when {
        this is java.net.InetSocketAddress -> InetSocketAddress(hostName, port)
        this.javaClass.name == UNIX_DOMAIN_SOCKET_ADDRESS_CLASS -> {
            val getPath = Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS).getMethod("getPath")
            UnixSocketAddress(getPath.invoke(this).toString())
        }
        else -> error("Unknown socket address type")
    }
}

internal fun Any.checkSupportForUnixDomainSockets() = try {
    Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS, false, javaClass.classLoader)
} catch (e: ClassNotFoundException) {
    error("Unix domain sockets are unsupported before Java 16.")
}
