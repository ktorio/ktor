/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.network.tls.*

/**
 * A configuration for the [CIO] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig)
 */
public class CIOEngineConfig : HttpClientEngineConfig() {
    /**
     * Provides access to [Endpoint] settings.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig.endpoint)
     */
    public val endpoint: EndpointConfig = EndpointConfig()

    /**
     * Allows you to configure [HTTPS](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig.https)
     */
    public val https: TLSConfigBuilder = TLSConfigBuilder()

    /**
     * Specifies the maximum number of connections used to make [requests](https://ktor.io/docs/request.html).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig.maxConnectionsCount)
     */
    public var maxConnectionsCount: Int = 1000

    /**
     * Specifies a request timeout in milliseconds.
     * The request timeout is the time period required to process an HTTP call:
     * from sending a request to receiving a response.
     *
     * To disable this timeout, set its value to `0`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig.requestTimeout)
     */
    public var requestTimeout: Long = 15000

    /**
     * Allows you to configure [HTTPS](https://ktor.io/docs/client-ssl.html) settings for this engine.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.CIOEngineConfig.https)
     */
    public fun https(block: TLSConfigBuilder.() -> Unit): TLSConfigBuilder = https.apply(block)
}

/**
 * Provides access to [Endpoint] settings.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.endpoint)
 */
public fun CIOEngineConfig.endpoint(block: EndpointConfig.() -> Unit): EndpointConfig = endpoint.apply(block)

/**
 * Contains [Endpoint] settings.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig)
 */
public class EndpointConfig {
    /**
     * Specifies the maximum number of connections for each host.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.maxConnectionsPerRoute)
     *
     * @see [CIOEngineConfig.maxConnectionsCount]
     */
    public var maxConnectionsPerRoute: Int = 100

    /**
     * Specifies a connection keep-alive time (in milliseconds).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.keepAliveTime)
     */
    public var keepAliveTime: Long = 5000

    /**
     * Specifies a maximum number of requests to be sent over a single connection without waiting for the corresponding responses (HTTP pipelining).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.pipelineMaxSize)
     */
    public var pipelineMaxSize: Int = 20

    /**
     * Specifies a time period (in milliseconds) in which a client should establish a connection with a server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.connectTimeout)
     */
    public var connectTimeout: Long = 5000

    /**
     * Specifies a maximum time (in milliseconds) of inactivity between two data packets when exchanging data with a server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.socketTimeout)
     */
    public var socketTimeout: Long = HttpTimeoutConfig.INFINITE_TIMEOUT_MS

    /**
     * Specifies a maximum number of connection attempts.
     * Note: this property affects only connection retries, but not request retries.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.connectAttempts)
     */
    public var connectAttempts: Int = 1

    /**
     * Allows a socket to close an output channel immediately on writing completion (half-closed TCP connection).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.cio.EndpointConfig.allowHalfClose)
     */
    public var allowHalfClose: Boolean = false
        @Deprecated("Half closed TCP connection is not supported by all servers, use it at your own risk.")
        set
}
