/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.util.*

/**
 * Base configuration for [HttpClientEngine].
 */
@HttpClientDsl
public open class HttpClientEngineConfig {
    /**
     * Network threads count advice.
     */
    public var threadsCount: Int = 4

    /**
     * Enable http pipelining advice.
     */
    public var pipelining: Boolean = false

    /**
     * Proxy address to use. Use system proxy by default.
     *
     * See [ProxyBuilder] to create proxy.
     */
    public var proxy: ProxyConfig? = null

    @Deprecated(
        "Response config is deprecated. See [HttpPlainText] feature for charset configuration",
        level = DeprecationLevel.ERROR
    )
    public val response: Nothing get() =
        error("Unbound [HttpClientCall] is deprecated. Consider using [request<HttpResponse>(block)] instead.")
}
