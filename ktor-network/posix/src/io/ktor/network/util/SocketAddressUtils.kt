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
        val octets = ipString.split('.').also {
            require(it.size == 4) { "Invalid IPv4 string: $ipString" }
        }

        octets.map { it.toUByte().toByte() }.toByteArray()
    } catch (_: Throwable) {
        null
    }
}

internal fun parseIPv6String(ipString: String): ByteArray? {
    return try {
        val groups = if ("::" in ipString) {
            val parts = ipString.split("::", limit = 2)

            val groups = Pair(
                if (parts[0].isNotEmpty()) parts[0].split(':') else emptyList(),
                if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].split(':') else emptyList()
            )

            val totalGroups = groups.first.size + groups.second.size
            val emptyGroups = 8 - totalGroups

            groups.first + List(emptyGroups) { "0" } + groups.second
        } else {
            ipString.split(':').also {
                require(it.size == 8) { "Invalid IPv6 string: $ipString" }
            }
        }

        groups.flatMap {
            val int = it.toInt(16)
            listOf((int shr 8).toByte(), int.toByte())
        }.toByteArray()
    } catch (_: Throwable) {
        null
    }
}
