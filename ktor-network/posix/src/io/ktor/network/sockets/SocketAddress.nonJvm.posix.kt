/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.*

internal actual fun InetSocketAddress.platformResolveAddress(): ByteArray? {
    return this.resolve().firstOrNull()?.let {
        when (it) {
            is NativeInetSocketAddress -> it.rawAddressBytes
            else -> null
        }
    }
}
