/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.engine.*
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy
import javax.net.ssl.SSLContext

/**
 * A configuration for the [Apache5] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig)
 */
public class Apache5EngineConfig : HttpClientEngineConfig() {
    /**
     * Specifies whether to follow redirects automatically.
     * Disabled by default.
     *
     * _Note: By default, the Apache client allows `50` redirects._
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.followRedirects)
     */
    public var followRedirects: Boolean = false

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * Set this value to `0` to use an infinite timeout.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.socketTimeout)
     */
    public var socketTimeout: Int = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.connectTimeout)
     */
    public var connectTimeout: Long = 10_000

    /**
     * Specifies a time period (in milliseconds) in which a client should start a request.
     *
     * A `0` value represents an infinite timeout, while `-1` represents a system's default value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.connectionRequestTimeout)
     */
    public var connectionRequestTimeout: Long = 20_000

    /**
     * Allows you to configure [SSL](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.sslContext)
     */
    public var sslContext: SSLContext? = null

    /**
     * Specifies the policy for verifying hostnames during SSL/TLS connections.
     *
     * The policy determines when hostname verification occurs during the connection process:
     * - During TLS handshake (by JSSE)
     * - After TLS handshake (by HttpClient)
     * - Or both (default)
     *
     * Default value is [HostnameVerificationPolicy.BOTH] which provides maximum security
     * by performing verification at both stages.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.sslHostnameVerificationPolicy)
     *
     * @see HostnameVerificationPolicy
     */
    public var sslHostnameVerificationPolicy: HostnameVerificationPolicy = HostnameVerificationPolicy.BOTH

    internal var customRequest: (RequestConfig.Builder.() -> RequestConfig.Builder) = { this }

    internal var customClient: (HttpAsyncClientBuilder.() -> HttpAsyncClientBuilder) = { this }

    /**
     * Customizes a [RequestConfig.Builder] in the specified [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.customizeRequest)
     */
    public fun customizeRequest(block: RequestConfig.Builder.() -> Unit) {
        val current = customRequest
        customRequest = { current().apply(block) }
    }

    /**
     * Customizes a [HttpAsyncClientBuilder] in the specified [block].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5EngineConfig.customizeClient)
     */
    public fun customizeClient(block: HttpAsyncClientBuilder.() -> Unit) {
        val current = customClient
        customClient = { current().apply(block) }
    }
}
