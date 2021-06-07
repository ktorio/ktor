/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.engine.*
import okhttp3.*

/**
 * Configuration for [OkHttp] client engine.
 */
public class OkHttpConfig : HttpClientEngineConfig() {

    internal var config: OkHttpClient.Builder.() -> Unit = {
        followRedirects(false)
        followSslRedirects(false)

        retryOnConnectionFailure(true)
    }

    /**
     * Preconfigured [OkHttpClient] instance instead of configuring one.
     */
    public var preconfigured: OkHttpClient? = null

    /**
     * Size of the cache that keeps least recently used [OkHttpClient] instances. Set "0" to avoid caching.
     */
    public var clientCacheSize: Int = 10

    /**
     * If provided, this [WebSocket.Factory] will be used to create [WebSocket] instances.
     * Otherwise, [OkHttpClient] is used directly.
     */
    public var webSocketFactory: WebSocket.Factory? = null

    /**
     * Configure [OkHttpClient] using [OkHttpClient.Builder].
     */
    public fun config(block: OkHttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }

    /**
     * Add [Interceptor] to [OkHttp] client.
     */
    public fun addInterceptor(interceptor: Interceptor) {
        config {
            addInterceptor(interceptor)
        }
    }

    /**
     * Add network [Interceptor] to [OkHttp] client.
     */
    public fun addNetworkInterceptor(interceptor: Interceptor) {
        config {
            addNetworkInterceptor(interceptor)
        }
    }
}
