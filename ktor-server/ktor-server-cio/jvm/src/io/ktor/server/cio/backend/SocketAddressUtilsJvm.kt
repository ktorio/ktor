/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.network.sockets.*
import io.ktor.util.network.*

internal actual fun SocketAddress.toNetworkAddress(): NetworkAddress {
    // Do not read the hostname here because that may trigger a name service reverse lookup.
    return toJavaAddress() as? java.net.InetSocketAddress ?: error("Expected inet socket address")
}
