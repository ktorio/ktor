/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

private const val UNIX_DOMAIN_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress"

internal actual fun Any.supportsUnixDomainSockets(): Boolean {
    return try {
        Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS, false, javaClass.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}
