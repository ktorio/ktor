/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*
import java.net.*

/**
 * Proxy configuration.
 *
 * See [ProxyBuilder] to create proxy.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyConfig)
 */
public actual typealias ProxyConfig = Proxy

/**
 * [ProxyConfig] factory.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder)
 */
public actual object ProxyBuilder {
    /**
     * Create http proxy from [url].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder.http)
     */
    public actual fun http(url: Url): ProxyConfig = Proxy(Proxy.Type.HTTP, InetSocketAddress(url.host, url.port))

    /**
     * Create socks proxy from [host] and [port].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder.socks)
     */
    public actual fun socks(host: String, port: Int): ProxyConfig =
        Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

/**
 * Resolve remote address of [ProxyConfig].
 *
 * This operation can block.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.resolveAddress)
 */
public actual fun ProxyConfig.resolveAddress(): NetworkAddress = address()

/**
 * Type of configured proxy.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.type)
 */
public actual val ProxyConfig.type: ProxyType
    get() = when (type()) {
        Proxy.Type.SOCKS -> ProxyType.SOCKS
        Proxy.Type.HTTP -> ProxyType.HTTP
        else -> ProxyType.UNKNOWN
    }
