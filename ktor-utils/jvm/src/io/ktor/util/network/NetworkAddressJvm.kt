/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

import java.net.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias NetworkAddress = SocketAddress

public actual val NetworkAddress.hostname: String
    get() = (this as? InetSocketAddress)?.hostName ?: ""

public actual val NetworkAddress.port: Int
    get() = (this as? InetSocketAddress)?.port ?: 0

public actual typealias UnresolvedAddressException = java.nio.channels.UnresolvedAddressException

public actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress {
    return InetSocketAddress(hostname, port)
}
