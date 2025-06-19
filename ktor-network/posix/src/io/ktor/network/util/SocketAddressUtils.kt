/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.posix.AF_UNIX

internal val SocketAddress.address: NativeSocketAddress get() {
    val explicitAddress = resolve().firstOrNull()
    return explicitAddress ?: error("Failed to resolve address for $this")
}

@OptIn(ExperimentalForeignApi::class)
internal fun SocketAddress.resolve(): List<NativeSocketAddress> = when (this) {
    is InetSocketAddress -> getAddressInfo(hostname, port)
    is UnixSocketAddress -> listOf(NativeUnixSocketAddress(AF_UNIX.convert(), path))
}
