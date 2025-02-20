/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import javax.net.ssl.SSLContext

/**
 * A configuration for the [Apache] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig)
 */
public class ApacheEngineConfig : HttpClientEngineConfig() {
    /**
     * Specifies whether to follow redirects automatically.
     * Disabled by default.
     *
     * _Note: By default, the Apache client allows `50` redirects._
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.followRedirects)
     */
    public var followRedirects: Boolean = false

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.socketTimeout)
     */
    public var socketTimeout: Int = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.connectTimeout)
     */
    public var connectTimeout: Int = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should start a request.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.connectionRequestTimeout)
     */
    public var connectionRequestTimeout: Int = 20_000

    /**
     * Allows you to configure [SSL](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.sslContext)
     */
    public var sslContext: SSLContext? = null

    /**
     * Specifies a custom processor for [RequestConfig.Builder].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.customRequest)
     */
    public var customRequest: (RequestConfig.Builder.() -> RequestConfig.Builder) = { this }
        private set

    /**
     * Specifies a custom processor for [HttpAsyncClientBuilder].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.customClient)
     */
    public var customClient: (HttpAsyncClientBuilder.() -> HttpAsyncClientBuilder) = { this }
        private set

    /**
     * Customizes a [RequestConfig.Builder] in the specified [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.customizeRequest)
     */
    public fun customizeRequest(block: RequestConfig.Builder.() -> Unit) {
        val current = customRequest
        customRequest = { current().apply(block) }
    }

    /**
     * Customizes a [HttpAsyncClientBuilder] in the specified [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.ApacheEngineConfig.customizeClient)
     */
    public fun customizeClient(block: HttpAsyncClientBuilder.() -> Unit) {
        val current = customClient
        customClient = { current().apply(block) }
    }
}
