/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.NativeIPv4SocketAddress
import io.ktor.network.util.NativeIPv6SocketAddress
import io.ktor.network.util.resolve

internal actual fun InetSocketAddress.platformResolveAddress(): ByteArray? {
    return this.resolve().firstOrNull()?.let {
        when (it) {
            is NativeIPv4SocketAddress -> {
                try {
                    val octets = it.ipString.split('.', limit = 4).map { o ->
                        o.toUByte().toByte()
                    }

                    List(4) { i ->
                        octets.getOrElse(i) { 0 }
                    }.toByteArray()
                } catch (_: Throwable) {
                    null
                }
            }
            is NativeIPv6SocketAddress -> {
                try {
                    val groups = it.ipString.split(':', limit = 8)
                    val emptyGroups = 8 - groups.count { g -> g.isNotEmpty() }

                    val bytes = groups.flatMap { g ->
                        if (g.isEmpty()) {
                            List(emptyGroups * 2) { 0 }
                        } else {
                            val int = g.toInt(16)
                            listOf((int shr 8).toByte(), int.toByte())
                        }
                    }
                    List(16) { i -> bytes.getOrElse(i) { 0 } }.toByteArray()
                } catch (_: Throwable) {
                    null
                }
            }
            else -> null
        }
    }
}
