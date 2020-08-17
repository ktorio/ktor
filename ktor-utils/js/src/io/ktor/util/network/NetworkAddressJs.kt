/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.network

public actual class NetworkAddress internal constructor(
    internal val hostname: String,
    internal val port: Int,
    internal val address: String
)

public actual val NetworkAddress.hostname: String
    get() = hostname

public actual val NetworkAddress.port: Int
    get() = port

public actual class UnresolvedAddressException : IllegalArgumentException()

actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress =
    io.ktor.util.network.NetworkAddress(hostname, port, hostname)
