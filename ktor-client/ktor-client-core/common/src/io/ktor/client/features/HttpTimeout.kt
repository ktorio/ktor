/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.concurrent.*
import kotlinx.coroutines.*

/**
 * Client HTTP timeout feature. There are no default values, so default timeouts will be taken from engine configuration
 * or considered as infinite time if engine doesn't provide them.
 */
public class HttpTimeout(
    private val requestTimeoutMillis: Long?,
    private val connectTimeoutMillis: Long?,
    private val socketTimeoutMillis: Long?
) {
    /**
     * [HttpTimeout] extension configuration that is used during installation.
     */
    public class HttpTimeoutCapabilityConfiguration {
        private var _requestTimeoutMillis: Long? by shared(0)
        private var _connectTimeoutMillis: Long? by shared(0)
        private var _socketTimeoutMillis: Long? by shared(0)

        /**
         * Creates a new instance of [HttpTimeoutCapabilityConfiguration].
         */
        @InternalAPI
        public constructor(
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
        public var requestTimeoutMillis: Long?
            get() = _requestTimeoutMillis
            set(value) {
                _requestTimeoutMillis = checkTimeoutValue(value)
            }

        /**
         * Connect timeout in milliseconds.
         */
        public var connectTimeoutMillis: Long?
            get() = _connectTimeoutMillis
            set(value) {
                _connectTimeoutMillis = checkTimeoutValue(value)
            }

        /**
         * Socket timeout (read and write) in milliseconds.
         */
        public var socketTimeoutMillis: Long?
            get() = _socketTimeoutMillis
            set(value) {
                _socketTimeoutMillis = checkTimeoutValue(value)
            }

        internal fun build(): HttpTimeout = HttpTimeout(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)

        private fun checkTimeoutValue(value: Long?): Long? {
            require(value == null || value > 0) {
                "Only positive timeout values are allowed, for infinite timeout use HttpTimeout.INFINITE_TIMEOUT_MS"
            }
            return value
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as HttpTimeoutCapabilityConfiguration

            if (_requestTimeoutMillis != other._requestTimeoutMillis) return false
            if (_connectTimeoutMillis != other._connectTimeoutMillis) return false
            if (_socketTimeoutMillis != other._socketTimeoutMillis) return false

            return true
        }

        override fun hashCode(): Int {
            var result = _requestTimeoutMillis?.hashCode() ?: 0
            result = 31 * result + (_connectTimeoutMillis?.hashCode() ?: 0)
            result = 31 * result + (_socketTimeoutMillis?.hashCode() ?: 0)
            return result
        }

        public companion object {
            public val key: AttributeKey<HttpTimeoutCapabilityConfiguration> = AttributeKey("TimeoutConfiguration")
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
    public companion object Feature :
        HttpClientFeature<HttpTimeoutCapabilityConfiguration, HttpTimeout>,
        HttpClientEngineCapability<HttpTimeoutCapabilityConfiguration> {

        override val key: AttributeKey<HttpTimeout> = AttributeKey("TimeoutFeature")

        /**
         * Infinite timeout in milliseconds.
         */
        public const val INFINITE_TIMEOUT_MS: Long = Long.MAX_VALUE

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
public fun HttpRequestBuilder.timeout(block: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit): Unit =
    setCapability(HttpTimeout, HttpTimeout.HttpTimeoutCapabilityConfiguration().apply(block))

/**
 * This exception is thrown in case request timeout exceeded.
 */
public class HttpRequestTimeoutException(
    request: HttpRequestBuilder
) : CancellationException(
    "Request timeout has expired [url=${request.url.buildString()}, " +
        "request_timeout=${request.getCapabilityOrNull(HttpTimeout)?.requestTimeoutMillis ?: "unknown"} ms]"
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public fun ConnectTimeoutException(
    request: HttpRequestData,
    cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has expired [url=${request.url}, " +
        "connect_timeout=${request.getCapabilityOrNull(HttpTimeout)?.connectTimeoutMillis ?: "unknown"} ms]",
    cause
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public fun ConnectTimeoutException(
    url: String,
    timeout: Long?,
    cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has expired [url=$url, connect_timeout=${timeout ?: "unknown"} ms]",
    cause
)

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
public fun SocketTimeoutException(
    request: HttpRequestData,
    cause: Throwable? = null
): SocketTimeoutException = SocketTimeoutException(
    "Socket timeout has expired [url=${request.url}, " +
        "socket_timeout=${request.getCapabilityOrNull(HttpTimeout)?.socketTimeoutMillis ?: "unknown"}] ms",
    cause
)

/**
 * Convert long timeout in milliseconds to int value. To do that we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
public fun convertLongTimeoutToIntWithInfiniteAsZero(timeout: Long): Int = when {
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
public fun convertLongTimeoutToLongWithInfiniteAsZero(timeout: Long): Long = when (timeout) {
    HttpTimeout.INFINITE_TIMEOUT_MS -> 0L
    else -> timeout
}
