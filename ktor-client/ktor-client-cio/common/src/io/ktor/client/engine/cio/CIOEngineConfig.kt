/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.network.tls.*

/**
 * Configuration for [CIO] client engine.
 */
public class CIOEngineConfig : HttpClientEngineConfig() {
    /**
     * [Endpoint] settings.
     */
    public val endpoint: EndpointConfig = EndpointConfig()

    /**
     * [https] settings.
     */
    public val https: TLSConfigBuilder = TLSConfigBuilder()

    /**
     * Maximum allowed connections count.
     */
    public var maxConnectionsCount: Int = 1000

    /**
     * Timeout to get send request headers and get first response bytes(in millis).
     *
     * Use 0 to disable.
     */
    public var requestTimeout: Long = 15000

    /**
     * [https] settings.
     */
    public fun https(block: TLSConfigBuilder.() -> Unit): TLSConfigBuilder = https.apply(block)
}

/**
 * Configure [endpoint] settings.
 */
public fun CIOEngineConfig.endpoint(block: EndpointConfig.() -> Unit): EndpointConfig = endpoint.apply(block)

/**
 * [Endpoint] settings.
 */
public class EndpointConfig {
    /**
     * Maximum connections  per single route.
     */
    public var maxConnectionsPerRoute: Int = 100

    /**
     * Connection keep-alive time in millis.
     */
    public var keepAliveTime: Long = 5000

    /**
     * Maximum number of requests per single pipeline.
     */
    public var pipelineMaxSize: Int = 20

    /**
     * Connect timeout in millis.
     */
    public var connectTimeout: Long = 5000

    /**
     * Socket timeout in millis.
     */
    public var socketTimeout: Long = HttpTimeout.INFINITE_TIMEOUT_MS

    /**
     * Maximum number of connection attempts.
     */
    @Deprecated(
        "This is deprecated due to the misleading name. Use connectAttempts instead.",
        replaceWith = ReplaceWith("connectAttempts")
    )
    public var connectRetryAttempts: Int
        get() = connectAttempts
        set(value) {
            connectAttempts = value
        }

    /**
     * Maximum number of connection attempts.
     * Note: this property affects only connection retries, but not request retries
     */
    public var connectAttempts: Int = 1

    /**
     * Allow socket to close output channel immediately on writing completion (TCP connection half close).
     */
    public var allowHalfClose: Boolean = false
        @Deprecated("Half closed TCP connection is not supported by all servers, use it at your own risk.")
        set
}
