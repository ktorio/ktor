/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.NativeIPv4SocketAddress
import io.ktor.network.util.NativeIPv6SocketAddress
import io.ktor.network.util.parseIPv4String
import io.ktor.network.util.parseIPv6String
import io.ktor.network.util.resolve

internal actual fun InetSocketAddress.platformResolveAddress(): ByteArray? {
    return this.resolve().firstOrNull()?.let {
        when (it) {
            is NativeIPv4SocketAddress -> parseIPv4String(it.ipString)
            is NativeIPv6SocketAddress -> parseIPv6String(it.ipString)
            else -> null
        }
    }
}
