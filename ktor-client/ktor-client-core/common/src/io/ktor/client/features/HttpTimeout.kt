/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

/**
 * Client HTTP timeout feature. There are no default values, so default timeouts will be taken from engine configuration
 * or considered as infinite time if engine doesn't provide them.
 */
class HttpTimeout(
    private val requestTimeoutMillis: Long?,
    private val connectTimeoutMillis: Long?,
    private val socketTimeoutMillis: Long?
) {
    /**
     * [HttpTimeout] extension configuration that is used during installation.
     */
    class HttpTimeoutCapabilityConfiguration {
        /**
         * Creates a new instance of [HttpTimeoutCapabilityConfiguration].
         */
        @InternalAPI
        constructor(
            requestTimeoutMillis: Long? = null,
            connectTimeoutMillis: Long? = null,
            socketTimeoutMillis: Long? = null
        ) {
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
        }

        /**
         * Request timeout in milliseconds.
         */
        var requestTimeoutMillis: Long?
            set(value) {
                field = checkTimeoutValue(value)
            }

        /**
         * Connect timeout in milliseconds.
         */
        var connectTimeoutMillis: Long?
            set(value) {
                field = checkTimeoutValue(value)
            }

        /**
         * Socket timeout (read and write) in milliseconds.
         */
        var socketTimeoutMillis: Long?
            set(value) {
                field = checkTimeoutValue(value)
            }

        internal fun build(): HttpTimeout = HttpTimeout(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)

        private fun checkTimeoutValue(value: Long?): Long? {
            require(value == null || value > 0) {
                "Only positive timeout values are allowed, for infinite timeout use HttpTimeout.INFINITE_TIMEOUT_MS"
            }
            return value
        }

        companion object {
            val key = AttributeKey<HttpTimeoutCapabilityConfiguration>("TimeoutConfiguration")
        }
    }

    /**
     * Utils method that return true if at least one timeout is configured (has not null value).
     */
    private fun hasNotNullTimeouts() =
        requestTimeoutMillis != null || connectTimeoutMillis != null || socketTimeoutMillis != null

    /**
     * Companion object for feature installation.
     */
    companion object Feature : HttpClientFeature<HttpTimeoutCapabilityConfiguration, HttpTimeout>,
        HttpClientEngineCapability<HttpTimeoutCapabilityConfiguration> {

        override val key: AttributeKey<HttpTimeout> = AttributeKey("TimeoutFeature")

        /**
         * Infinite timeout in milliseconds.
         */
        const val INFINITE_TIMEOUT_MS = Long.MAX_VALUE

        override fun prepare(block: HttpTimeoutCapabilityConfiguration.() -> Unit): HttpTimeout =
            HttpTimeoutCapabilityConfiguration().apply(block).build()

        override fun install(feature: HttpTimeout, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                var configuration = context.getCapabilityOrNull(HttpTimeout)
                if (configuration == null && feature.hasNotNullTimeouts()) {
                    configuration = HttpTimeoutCapabilityConfiguration()
                    context.setCapability(HttpTimeout, configuration)
                }

                configuration?.apply {
                    connectTimeoutMillis = connectTimeoutMillis ?: feature.connectTimeoutMillis
                    socketTimeoutMillis = socketTimeoutMillis ?: feature.socketTimeoutMillis
                    requestTimeoutMillis = requestTimeoutMillis ?: feature.requestTimeoutMillis

                    val requestTimeout = requestTimeoutMillis ?: feature.requestTimeoutMillis
                    if (requestTimeout == null || requestTimeout == INFINITE_TIMEOUT_MS) return@apply

                    val executionContext = context.executionContext
                    val killer = scope.launch {
                        delay(requestTimeout)
                        executionContext.cancel(HttpRequestTimeoutException(context))
                    }

                    context.executionContext.invokeOnCompletion {
                        killer.cancel()
                    }
                }
            }
        }
    }
}

/**
 * Adds timeout boundaries to the request. Requires [HttpTimeout] feature to be installed.
 */
fun HttpRequestBuilder.timeout(block: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit) =
    setCapability(HttpTimeout, HttpTimeout.HttpTimeoutCapabilityConfiguration().apply(block))

/**
 * This exception is thrown in case request timeout exceeded.
 */
class HttpRequestTimeoutException(
    request: HttpRequestBuilder
) : CancellationException(
    "Request timeout has been expired [url=${request.url.buildString()}, request_timeout=${request.getCapabilityOrNull(
        HttpTimeout
    )?.requestTimeoutMillis ?: "unknown"} ms]"
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
fun ConnectTimeoutException(
    request: HttpRequestData, cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has been expired [url=${request.url}, connect_timeout=${request.getCapabilityOrNull(
        HttpTimeout
    )?.connectTimeoutMillis ?: "unknown"} ms]",
    cause
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
fun ConnectTimeoutException(
    url: String, timeout: Long?, cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has been expired [url=$url, connect_timeout=${timeout ?: "unknown"} ms]",
    cause
)

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
fun SocketTimeoutException(
    request: HttpRequestData,
    cause: Throwable? = null
): SocketTimeoutException = SocketTimeoutException(
    "Socket timeout has been expired [url=${request.url}, socket_timeout=${request.getCapabilityOrNull(
        HttpTimeout
    )?.socketTimeoutMillis ?: "unknown"}] ms",
    cause
)

/**
 * Convert long timeout in milliseconds to int value. To do that we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
fun convertLongTimeoutToIntWithInfiniteAsZero(timeout: Long): Int = when {
    timeout == HttpTimeout.INFINITE_TIMEOUT_MS -> 0
    timeout < Int.MIN_VALUE -> Int.MIN_VALUE
    timeout > Int.MAX_VALUE -> Int.MAX_VALUE
    else -> timeout.toInt()
}

/**
 * Convert long timeout in milliseconds to long value. To do that we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
fun convertLongTimeoutToLongWithInfiniteAsZero(timeout: Long): Long = when (timeout) {
    HttpTimeout.INFINITE_TIMEOUT_MS -> 0L
    else -> timeout
}
