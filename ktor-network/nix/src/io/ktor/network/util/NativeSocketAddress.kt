/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.cinterop.*
import platform.linux.*
import platform.posix.*

/**
 * Represents a native socket address.
 */
internal sealed class NativeSocketAddress(
    val family: sa_family_t,
) {
    internal abstract fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit)
}

/**
 * Represents an INET socket address.
 */
internal sealed class NativeInetSocketAddress(
    family: sa_family_t,
    val port: Int
) : NativeSocketAddress(family) {
    internal abstract val ipString: String
}

internal class NativeIPv4SocketAddress(
    family: sa_family_t,
    private val rawAddress: in_addr,
    port: Int
) : NativeInetSocketAddress(family, port) {
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        cValue<sockaddr_in> {
            sin_addr.s_addr = rawAddress.s_addr
            sin_port = htons(port.toUShort())
            sin_family = family

            block(ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
    }

    override val ipString: String
        get() = memScoped {
            val string = allocArray<ByteVar>(INET_ADDRSTRLEN)
            inet_ntop(family.convert(), rawAddress.ptr, string.reinterpret(), INET_ADDRSTRLEN)?.toKString()
                ?: error("Failed to convert address to text")
        }
}

internal class NativeIPv6SocketAddress(
    family: sa_family_t,
    private val rawAddress: in6_addr,
    port: Int,
    private val flowInfo: uint32_t,
    private val scopeId: uint32_t
) : NativeInetSocketAddress(family, port) {
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        cValue<sockaddr_in6> {
            sin6_family = family
            sin6_flowinfo = flowInfo
            sin6_port = htons(port.toUShort())
            sin6_scope_id = scopeId

            block(ptr.reinterpret(), sizeOf<sockaddr_in6>().convert())
        }
    }

    override val ipString: String
        get() = memScoped {
            val string = allocArray<ByteVar>(INET6_ADDRSTRLEN)
            inet_ntop(family.convert(), rawAddress.ptr, string.reinterpret(), INET6_ADDRSTRLEN)?.toKString()
                ?: error("Failed to convert address to text")
        }
}

/**
 * Represents an UNIX socket address.
 */
internal class NativeUnixSocketAddress(
    family: sa_family_t,
    val path: String,
) : NativeSocketAddress(family) {
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        cValue<sockaddr_un> {
            strcpy(sun_path, path)
            sun_family = family

            block(ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
        }
    }
}

internal fun NativeSocketAddress.toSocketAddress(): SocketAddress = when (this) {
    is NativeInetSocketAddress -> InetSocketAddress(ipString, port)
    is NativeUnixSocketAddress -> UnixSocketAddress(path)
}
