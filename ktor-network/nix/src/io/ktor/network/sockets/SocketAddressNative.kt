package io.ktor.network.sockets

public actual sealed class SocketAddress

public actual class InetSocketAddress actual constructor(
    public actual val hostname: String,
    public actual val port: Int
) : SocketAddress() {
    /**
     * Create a copy of [InetSocketAddress].
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public actual fun copy(hostname: String, port: Int): InetSocketAddress {
        return InetSocketAddress(hostname, port)
    }

    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public actual operator fun component1(): String {
        return hostname
    }

    /**
     * The port number of the socket address.
     */
    public actual operator fun component2(): Int {
        return port
    }
}

public actual class UnixSocketAddress actual constructor(
    public actual val path: String
) : SocketAddress() {
    /**
     * The path of the socket address.
     */
    public actual operator fun component1(): String {
        return path
    }

    /**
     * Create a copy of [UnixSocketAddress].
     */
    public actual fun copy(path: String): UnixSocketAddress {
        return UnixSocketAddress(path)
    }
}
