/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.http.*
import io.ktor.util.network.*

/**
 * A [proxy](https://ktor.io/docs/proxy.html) configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyConfig)
 */
public expect class ProxyConfig

/**
 * A type of the configured proxy.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.type)
 */
public expect val ProxyConfig.type: ProxyType

/**
 * A [proxy](https://ktor.io/docs/proxy.html) type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyType)
 */
@Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "KDocMissingDocumentation")
public enum class ProxyType {
    SOCKS,
    HTTP,
    UNKNOWN
}

/**
 * Resolves a remote address of [ProxyConfig].
 *
 * This operation can block.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.resolveAddress)
 */
public expect fun ProxyConfig.resolveAddress(): NetworkAddress

/**
 * A [ProxyConfig] factory.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder)
 *
 * @see [io.ktor.client.engine.HttpClientEngineConfig.proxy]
 */
public expect object ProxyBuilder {
    /**
     * Creates an HTTP proxy from [url].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder.http)
     */
    public fun http(url: Url): ProxyConfig

    /**
     * Creates a socks proxy from [host] and [port].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.ProxyBuilder.socks)
     */
    public fun socks(host: String, port: Int): ProxyConfig
}

/**
 * Creates an HTTP proxy from [urlString].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.http)
 */
public fun ProxyBuilder.http(urlString: String): ProxyConfig = http(Url(urlString))
