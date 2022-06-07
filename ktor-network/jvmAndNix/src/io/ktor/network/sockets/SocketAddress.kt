/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

public expect sealed class SocketAddress

public expect class InetSocketAddress(
    hostname: String,
    port: Int
) : SocketAddress {
    public val hostname: String
    public val port: Int

    public operator fun component1(): String
    public operator fun component2(): Int
    public fun copy(hostname: String = this.hostname, port: Int = this.port): InetSocketAddress
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}

public expect class UnixSocketAddress(
    path: String
) : SocketAddress {
    public val path: String

    public operator fun component1(): String
    public fun copy(path: String = this.path): UnixSocketAddress
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
