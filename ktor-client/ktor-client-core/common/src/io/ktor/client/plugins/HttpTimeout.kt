/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpTimeout")

/**
 * A plugin that allows you to configure the following timeouts:
 * - __request timeout__ — a time period required to process an HTTP call: from sending a request to receiving a response.
 * - __connection timeout__ — a time period in which a client should establish a connection with a server.
 * - __socket timeout__ — a maximum time of inactivity between two data packets when exchanging data with a server.
 *
 * You can learn more from [Timeout](https://ktor.io/docs/timeout.html).
 */
public class HttpTimeout private constructor(
    private val requestTimeoutMillis: Long?,
    private val connectTimeoutMillis: Long?,
    private val socketTimeoutMillis: Long?
) {
    /**
     * An [HttpTimeout] extension configuration that is used during installation.
     */
    @KtorDsl
    public class HttpTimeoutCapabilityConfiguration {
        private var _requestTimeoutMillis: Long? = 0
        private var _connectTimeoutMillis: Long? = 0
        private var _socketTimeoutMillis: Long? = 0

        /**
         * Creates a new instance of [HttpTimeoutCapabilityConfiguration].
         */
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
         * Specifies a request timeout in milliseconds.
         * The request timeout is the time period required to process an HTTP call: from sending a request to receiving a response.
         */
        public var requestTimeoutMillis: Long?
            get() = _requestTimeoutMillis
            set(value) {
                _requestTimeoutMillis = checkTimeoutValue(value)
            }

        /**
         * Specifies a connection timeout in milliseconds.
         * The connection timeout is the time period in which a client should establish a connection with a server.
         */
        public var connectTimeoutMillis: Long?
            get() = _connectTimeoutMillis
            set(value) {
                _connectTimeoutMillis = checkTimeoutValue(value)
            }

        /**
         * Specifies a socket timeout (read and write) in milliseconds.
         * The socket timeout is the maximum time of inactivity between two data packets when exchanging data with a server.
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
     * Utils method that return `true` if at least one timeout is configured (has not null value).
     */
    private fun hasNotNullTimeouts() =
        requestTimeoutMillis != null || connectTimeoutMillis != null || socketTimeoutMillis != null

    /**
     * A companion object for a plugin installation.
     */
    public companion object Plugin :
        HttpClientPlugin<HttpTimeoutCapabilityConfiguration, HttpTimeout>,
        HttpClientEngineCapability<HttpTimeoutCapabilityConfiguration> {

        override val key: AttributeKey<HttpTimeout> = AttributeKey("TimeoutPlugin")

        /**
         * An infinite timeout in milliseconds.
         */
        public const val INFINITE_TIMEOUT_MS: Long = Long.MAX_VALUE

        override fun prepare(block: HttpTimeoutCapabilityConfiguration.() -> Unit): HttpTimeout =
            HttpTimeoutCapabilityConfiguration().apply(block).build()

        @OptIn(InternalAPI::class)
        override fun install(plugin: HttpTimeout, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val isWebSocket = request.url.protocol.isWebsocket()
                if (isWebSocket || request.body is ClientUpgradeContent) return@intercept execute(request)

                var configuration = request.getCapabilityOrNull(HttpTimeout)
                if (configuration == null && plugin.hasNotNullTimeouts()) {
                    configuration = HttpTimeoutCapabilityConfiguration()
                    request.setCapability(HttpTimeout, configuration)
                }

                configuration?.apply {
                    connectTimeoutMillis = connectTimeoutMillis ?: plugin.connectTimeoutMillis
                    socketTimeoutMillis = socketTimeoutMillis ?: plugin.socketTimeoutMillis
                    requestTimeoutMillis = requestTimeoutMillis ?: plugin.requestTimeoutMillis

                    val requestTimeout = requestTimeoutMillis ?: plugin.requestTimeoutMillis
                    if (requestTimeout == null || requestTimeout == INFINITE_TIMEOUT_MS) return@apply

                    val executionContext = request.executionContext
                    val killer = scope.launch {
                        delay(requestTimeout)
                        val cause = HttpRequestTimeoutException(request)
                        LOGGER.trace("Request timeout: ${request.url}")
                        executionContext.cancel(cause.message!!, cause)
                    }

                    request.executionContext.invokeOnCompletion {
                        killer.cancel()
                    }
                }
                execute(request)
            }
        }
    }
}

/**
 * Adds timeout boundaries to the request. Requires the [HttpTimeout] plugin to be installed.
 */
public fun HttpRequestBuilder.timeout(block: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit): Unit =
    setCapability(HttpTimeout, HttpTimeout.HttpTimeoutCapabilityConfiguration().apply(block))

/**
 * This exception is thrown in case the request timeout is exceeded.
 * The request timeout is the time period required to process an HTTP call: from sending a request to receiving a response.
 */
public class HttpRequestTimeoutException(
    url: String,
    timeoutMillis: Long?
) : IOException("Request timeout has expired [url=$url, request_timeout=${timeoutMillis ?: "unknown"} ms]") {

    public constructor(request: HttpRequestBuilder) : this(
        request.url.buildString(),
        request.getCapabilityOrNull(HttpTimeout)?.requestTimeoutMillis
    )

    public constructor(request: HttpRequestData) : this(
        request.url.toString(),
        request.getCapabilityOrNull(HttpTimeout)?.requestTimeoutMillis
    )
}

/**
 * This exception is thrown in case the connection timeout is exceeded.
 * It indicates the client took too long to establish a connection with a server.
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
 * This exception is thrown in case the connection timeout is exceeded.
 * It indicates the client took too long to establish a connection with a server.
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
 * This exception is thrown in case the socket timeout (read or write) is exceeded.
 * It indicates the time between two data packets when exchanging data with a server was too long.
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
 * Converts a long timeout in milliseconds to int value. To do that, we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
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
 * Converts long timeout in milliseconds to long value. To do that, we need to consider [HttpTimeout.INFINITE_TIMEOUT_MS]
 * as zero and convert timeout value to [Int].
 */
@InternalAPI
public fun convertLongTimeoutToLongWithInfiniteAsZero(timeout: Long): Long = when (timeout) {
    HttpTimeout.INFINITE_TIMEOUT_MS -> 0L
    else -> timeout
}

@PublishedApi
internal inline fun <T> unwrapRequestTimeoutException(block: () -> T): T {
    try {
        return block()
    } catch (cause: CancellationException) {
        throw cause.unwrapCancellationException()
    }
}
