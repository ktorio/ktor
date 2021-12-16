/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.util.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

/**
 * A client's HTTP timeout plugin. There are no default values, so default timeouts are taken from the
 * engine configuration or considered as infinite time if the engine doesn't provide them.
 */
public class HttpTimeout private constructor(
    private val requestTimeout: Duration?,
    private val connectTimeout: Duration?,
    private val socketTimeout: Duration?
) {
    /**
     * [HttpTimeout] extension configuration that is used during installation.
     */
    public class HttpTimeoutCapabilityConfiguration {
        private var _requestTimeout: Duration? by shared(Duration.ZERO)
        private var _connectTimeout: Duration? by shared(Duration.ZERO)
        private var _socketTimeout: Duration? by shared(Duration.ZERO)

        /**
         * Creates a new instance of [HttpTimeoutCapabilityConfiguration].
         */
        public constructor(
            requestTimeout: Duration? = null,
            connectTimeout: Duration? = null,
            socketTimeout: Duration? = null
        ) {
            this.requestTimeout = requestTimeout
            this.connectTimeout = connectTimeout
            this.socketTimeout = socketTimeout
        }

        /**
         * Request timeout.
         */
        public var requestTimeout: Duration?
            get() = _requestTimeout
            set(value) {
                _requestTimeout = checkTimeoutValue(value)
            }

        /**
         * Connect timeout.
         */
        public var connectTimeout: Duration?
            get() = _connectTimeout
            set(value) {
                _connectTimeout = checkTimeoutValue(value)
            }

        /**
         * Socket timeout (read and write).
         */
        public var socketTimeout: Duration?
            get() = _socketTimeout
            set(value) {
                _socketTimeout = checkTimeoutValue(value)
            }

        internal fun build(): HttpTimeout = HttpTimeout(requestTimeout, connectTimeout, socketTimeout)

        private fun checkTimeoutValue(value: Duration?): Duration? {
            require(value == null || value.isPositive()) {
                "Only positive timeout values are allowed, for infinite timeout use Duration.INFINITE"
            }
            return value
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as HttpTimeoutCapabilityConfiguration

            if (_requestTimeout != other._requestTimeout) return false
            if (_connectTimeout != other._connectTimeout) return false
            if (_socketTimeout != other._socketTimeout) return false

            return true
        }

        override fun hashCode(): Int {
            var result = _requestTimeout?.hashCode() ?: 0
            result = 31 * result + (_connectTimeout?.hashCode() ?: 0)
            result = 31 * result + (_socketTimeout?.hashCode() ?: 0)
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
        requestTimeout != null || connectTimeout != null || socketTimeout != null

    /**
     * Companion object for plugin installation.
     */
    public companion object Plugin :
        HttpClientPlugin<HttpTimeoutCapabilityConfiguration, HttpTimeout>,
        HttpClientEngineCapability<HttpTimeoutCapabilityConfiguration> {

        override val key: AttributeKey<HttpTimeout> = AttributeKey("TimeoutPlugin")

        override fun prepare(block: HttpTimeoutCapabilityConfiguration.() -> Unit): HttpTimeout =
            HttpTimeoutCapabilityConfiguration().apply(block).build()

        @OptIn(ExperimentalTime::class)
        override fun install(plugin: HttpTimeout, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                var configuration = context.getCapabilityOrNull(HttpTimeout)
                if (configuration == null && plugin.hasNotNullTimeouts()) {
                    configuration = HttpTimeoutCapabilityConfiguration()
                    context.setCapability(HttpTimeout, configuration)
                }

                configuration?.apply {
                    connectTimeout = connectTimeout ?: plugin.connectTimeout
                    socketTimeout = socketTimeout ?: plugin.socketTimeout
                    requestTimeout = requestTimeout ?: plugin.requestTimeout

                    val requestTimeout = requestTimeout ?: plugin.requestTimeout
                    if (requestTimeout == null || requestTimeout == Duration.INFINITE) return@apply

                    val executionContext = context.executionContext
                    val killer = scope.launch {
                        delay(requestTimeout)
                        val cause = HttpRequestTimeoutException(context)
                        executionContext.cancel(cause.message!!, cause)
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
 * Adds timeout boundaries to the request. Requires [HttpTimeout] plugin to be installed.
 */
public fun HttpRequestBuilder.timeout(block: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit): Unit =
    setCapability(HttpTimeout, HttpTimeout.HttpTimeoutCapabilityConfiguration().apply(block))

/**
 * This exception is thrown in case request timeout exceeded.
 */
public class HttpRequestTimeoutException(
    request: HttpRequestBuilder
) : IOException(
    "Request timeout has expired [url=${request.url.buildString()}, " +
        "request_timeout=${request.getCapabilityOrNull(HttpTimeout)?.requestTimeout ?: "unknown ms"}]"
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public fun ConnectTimeoutException(
    request: HttpRequestData,
    cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has expired [url=${request.url}, " +
        "connect_timeout=${request.getCapabilityOrNull(HttpTimeout)?.connectTimeout ?: "unknown ms"}]",
    cause
)

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public fun ConnectTimeoutException(
    url: String,
    timeout: Duration?,
    cause: Throwable? = null
): ConnectTimeoutException = ConnectTimeoutException(
    "Connect timeout has expired [url=$url, connect_timeout=${timeout ?: "unknown ms"}]",
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
        "socket_timeout=${request.getCapabilityOrNull(HttpTimeout)?.socketTimeout ?: "unknown ms"}]",
    cause
)

/**
 * Convert timeout to an int value in milliseconds. To do that we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
public fun convertTimeoutToIntWithInfiniteAsZero(timeout: Duration): Int = when (timeout) {
    INFINITE -> 0
    else -> timeout.toInt(DurationUnit.MILLISECONDS)
}

/**
 * Convert long timeout in milliseconds to long value. To do that we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
public fun convertTimeoutToLongWithInfiniteAsZero(timeout: Duration): Long = when (timeout) {
    INFINITE -> 0L
    else -> timeout.toLong(DurationUnit.MILLISECONDS)
}

@PublishedApi
internal inline fun <T> unwrapRequestTimeoutException(block: () -> T): T {
    try {
        return block()
    } catch (cause: CancellationException) {
        throw cause.unwrapCancellationException()
    }
}
