/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.ProxyConfig
import java.net.InetSocketAddress
import java.net.Proxy

internal actual fun discoverHttpProxy(): ProxyConfig? {
    var proxy: Proxy? = null

    try {
        // For Android
        val host = System.getProperty("http.proxyHost")
        val port = System.getProperty("http.proxyPort")?.toIntOrNull() ?: 0

        if (host != null && host.isNotEmpty()) {
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        }
    } catch (_: SecurityException) {}

    return proxy
}
