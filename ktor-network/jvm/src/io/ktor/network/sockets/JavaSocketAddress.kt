/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.lang.reflect.*

public actual sealed class SocketAddress {
    internal abstract val address: java.net.SocketAddress
}

public actual class InetSocketAddress internal constructor(
    override val address: java.net.InetSocketAddress
) : SocketAddress() {

    // May trigger a name service reverse lookup when called.
    public actual val hostname: String get() = address.hostName

    public actual val port: Int get() = address.port

    public actual constructor(hostname: String, port: Int) :
        this(java.net.InetSocketAddress(hostname, port))

    // May trigger a name service reverse lookup when called.
    public actual operator fun component1(): String = hostname

    public actual operator fun component2(): Int = port

    // May trigger a name service reverse lookup when called.
    public actual fun copy(
        hostname: String,
        port: Int
    ): InetSocketAddress = InetSocketAddress(
        hostname = hostname,
        port = port
    )

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InetSocketAddress

        if (address != other.address) return false

        return true
    }

    actual override fun hashCode(): Int {
        return address.hashCode()
    }

    public actual override fun toString(): String = address.toString()
}

public actual class UnixSocketAddress internal constructor(
    override val address: java.net.SocketAddress // actually: java.net.UnixDomainSocketAddress
) : SocketAddress() {

    init {
        checkSupportForUnixDomainSockets()
    }

    public actual val path: String
        get() {
            val getPath: Method = Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS).getMethod("getPath")
            return getPath.invoke(this).toString()
        }

    public actual constructor(path: String) : this(
        checkSupportForUnixDomainSockets().let {
            Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS)
                .getMethod("of", String::class.java)
                .invoke(null, path) as java.net.SocketAddress
        }
    )

    public actual operator fun component1(): String = path

    public actual fun copy(
        path: String,
    ): UnixSocketAddress = UnixSocketAddress(
        path = path
    )

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnixSocketAddress

        if (address != other.address) return false

        return true
    }

    actual override fun hashCode(): Int {
        return address.hashCode()
    }

    public actual override fun toString(): String = address.toString()

    private companion object {
        private fun checkSupportForUnixDomainSockets() {
            try {
                Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS, false, Companion::class.java.classLoader)
            } catch (e: ClassNotFoundException) {
                error("Unix domain sockets are unsupported before Java 16.")
            }
        }
    }
}

internal const val UNIX_DOMAIN_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress"
