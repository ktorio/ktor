/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

/**
 * Represents a socket address abstraction.
 *
 * This sealed class serves as the base type for different kinds of socket addresses,
 * such as Internet-specific or other platform-dependent address types.
 * Implementations of this class are expected to be platform-specific and provide
 * details necessary to work with socket connections or bindings.
 */
public expect sealed class SocketAddress

/**
 * Retrieves the port number associated with this socket address.
 *
 * If the `SocketAddress` instance is of type `InetSocketAddress`, the associated port is returned.
 * Otherwise, an `UnsupportedOperationException` is thrown, as the provided address type does not support ports.
 *
 * @return the port number of the socket address if available.
 * @throws UnsupportedOperationException if the socket address type does not support a port.
 */
public fun SocketAddress.port(): Int = when (this) {
    is InetSocketAddress -> port
    else -> throw UnsupportedOperationException("SocketAddress $this does not have a port")
}

public expect class InetSocketAddress(
    hostname: String,
    port: Int
) : SocketAddress {
    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.InetSocketAddress.hostname)
     */
    public val hostname: String

    /**
     * The port number of the socket address.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.InetSocketAddress.port)
     */
    public val port: Int

    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.InetSocketAddress.component1)
     */
    public operator fun component1(): String

    /**
     * The port number of the socket address.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.InetSocketAddress.component2)
     */
    public operator fun component2(): Int

    /**
     * Create a copy of [InetSocketAddress].
     *
     * Note that this may trigger a name service reverse lookup.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.InetSocketAddress.copy)
     */
    public fun copy(hostname: String = this.hostname, port: Int = this.port): InetSocketAddress

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}

public expect class UnixSocketAddress(
    path: String
) : SocketAddress {
    /**
     * The path of the socket address.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UnixSocketAddress.path)
     */
    public val path: String

    /**
     * The path of the socket address.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UnixSocketAddress.component1)
     */
    public operator fun component1(): String

    /**
     * Create a copy of [UnixSocketAddress].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UnixSocketAddress.copy)
     */
    public fun copy(path: String = this.path): UnixSocketAddress
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
