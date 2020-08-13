package io.ktor.network.util

import java.net.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias NetworkAddress = SocketAddress

public actual val NetworkAddress.hostname: String
    get() = (this as? InetSocketAddress)?.hostName ?: ""

public actual val NetworkAddress.port: Int
    get() = (this as? InetSocketAddress)?.port ?: 0

public actual typealias UnresolvedAddressException = java.nio.channels.UnresolvedAddressException

actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress {
    return InetSocketAddress(hostname, port)
}
