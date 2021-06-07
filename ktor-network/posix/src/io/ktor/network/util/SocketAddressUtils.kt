/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.util.network.*

public val NetworkAddress.address: SocketAddress get() {
    if (explicitAddress.value == null) {
        explicitAddress.value = resolve().first()
    }

    return explicitAddress.value as? SocketAddress ?: error("Failed to resolve address for $this")
}

public fun NetworkAddress.resolve(): List<SocketAddress> = getAddressInfo(hostname, port)
