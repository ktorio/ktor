package io.ktor.network.sockets

public actual sealed class SocketAddress

public actual data class InetSocketAddress actual constructor(
    public actual val hostname: String,
    public actual val port: Int
) : SocketAddress()

public actual data class UnixSocketAddress actual constructor(
    public actual val path: String
) : SocketAddress()
