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

internal fun parseIPv4String(ipString: String): ByteArray? {
    return try {
        val octets = ipString.split('.', limit = 4).map { o ->
            o.toUByte().toByte()
        }

        ByteArray(4) { octets.getOrElse(it) { 0 } }
    } catch (_: Throwable) {
        null
    }
}

internal fun parseIPv6String(ipString: String): ByteArray? {
    return try {
        val groups = ipString.split(':', limit = 8)
        val emptyGroups = 8 - groups.count { g -> g.isNotEmpty() }

        val bytes = groups.flatMap { g ->
            if (g.isEmpty()) {
                List(emptyGroups * 2) { 0 }
            } else {
                val int = g.toInt(16)
                listOf((int shr 8).toByte(), int.toByte())
            }
        }

        ByteArray(16) { bytes.getOrElse(it) { 0 } }
    } catch (_: Throwable) {
        null
    }
}
