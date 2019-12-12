/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.network.tls.*

/**
 * Configuration for [CIO] client engine.
 */
class CIOEngineConfig : HttpClientEngineConfig() {
    /**
     * [Endpoint] settings.
     */
    val endpoint: EndpointConfig = EndpointConfig()
    /**
     * [https] settings.
     */
    val https: TLSConfigBuilder = TLSConfigBuilder()

    /**
     * Maximum allowed connections count.
     */
    var maxConnectionsCount: Int = 1000

    /**
     * Timeout to get send request headers and get first response bytes(in millis).
     *
     * Use 0 to disable.
     */
    var requestTimeout: Long = 15000

    /**
     * [https] settings.
     */
    fun https(block: TLSConfigBuilder.() -> Unit): TLSConfigBuilder = https.apply(block)
}

/**
 * Configure [endpoint] settings.
 */
fun CIOEngineConfig.endpoint(block: EndpointConfig.() -> Unit): EndpointConfig = endpoint.apply(block)

/**
 * [Endpoint] settings.
 */
class EndpointConfig {
    /**
     * Maximum connections  per single route.
     */
    var maxConnectionsPerRoute: Int = 100

    /**
     * Connection keep-alive time in millis.
     */
    var keepAliveTime: Long = 5000

    /**
     * Maximum number of requests per single pipeline.
     */
    var pipelineMaxSize: Int = 20

    /**
     * Connect timeout in millis.
     */
    var connectTimeout: Long = 5000

    /**
     * Maximum number of connection attempts.
     */
    var connectRetryAttempts: Int = 5

    /**
     * Allow socket to close output channel immediately on writing completion (TCP connection half close).
     */
    var allowHalfClose: Boolean = false
        @Deprecated("Half closed TCP connection is not supported by all servers, use it at your own risk.")
        set
}
