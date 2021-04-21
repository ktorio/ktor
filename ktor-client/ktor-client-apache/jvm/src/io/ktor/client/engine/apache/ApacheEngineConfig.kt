/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import org.apache.http.client.config.*
import org.apache.http.impl.nio.client.*
import javax.net.ssl.*

/**
 * Configuration for [Apache] implementation of [HttpClientEngineFactory].
 */
public class ApacheEngineConfig : HttpClientEngineConfig() {
    /**
     * Whether or not, it will follow `Location` headers. `false` by default.
     * It uses the default number of redirects defined by Apache's HttpClient that is 50.
     */
    public var followRedirects: Boolean = false

    /**
     * Max milliseconds between TCP packets - default 10 seconds.
     * A value of 0 represents infinite, while -1 represents system's default value.
     */
    public var socketTimeout: Int = 10_000

    /**
     * Max milliseconds to establish an HTTP connection - default 10 seconds.
     * A value of 0 represents infinite, while -1 represents system's default value.
     */
    public var connectTimeout: Int = 10_000

    /**
     * Max milliseconds for the connection manager to start a request - default 20 seconds.
     * A value of 0 represents infinite, while -1 represents system's default value.
     */
    public var connectionRequestTimeout: Int = 20_000

    /**
     * Optional Java's SSLContext allowing to set custom keys,
     * trust manager or custom source for secure random data
     */
    public var sslContext: SSLContext? = null

    /**
     * Custom processor for [RequestConfig.Builder].
     */
    public var customRequest: (RequestConfig.Builder.() -> RequestConfig.Builder) = { this }
        private set

    /**
     * Custom processor for [HttpAsyncClientBuilder].
     */
    public var customClient: (HttpAsyncClientBuilder.() -> HttpAsyncClientBuilder) = { this }
        private set

    /**
     * Customizes a [RequestConfig.Builder] in the specified [block].
     */
    public fun customizeRequest(block: RequestConfig.Builder.() -> Unit) {
        val current = customRequest
        customRequest = { current(); block(); this }
    }

    /**
     * Customizes a [HttpAsyncClientBuilder] in the specified [block].
     */
    public fun customizeClient(block: HttpAsyncClientBuilder.() -> Unit) {
        val current = customClient
        customClient = { current(); block(); this }
    }
}
