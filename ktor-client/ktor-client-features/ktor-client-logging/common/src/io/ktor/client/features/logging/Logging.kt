/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * [HttpClient] logging feature.
 */
class Logging(
    val logger: Logger,
    var level: LogLevel,
    val filters: List<(HttpClientCall) -> Boolean>,
    val logContentType: LogContentType
) {
    /**
     * Constructor
     */
    constructor(logger: Logger, level: LogLevel) : this(logger, level, emptyList(), LogContentType.BOTH)

    /**
     * [Logging] feature configuration
     */
    class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpClientCall) -> Boolean>()

        /**
         * Log request/response/both
         */
        internal var logContentType = LogContentType.BOTH

        /**
         * [Logger] instance to use
         */
        var logger: Logger = Logger.DEFAULT

        /**
         * log [LogLevel]
         */
        var level: LogLevel = LogLevel.HEADERS

        /**
         * Log messages for calls matching a [predicate]
         */
        fun filter(predicate: (HttpClientCall) -> Boolean) {
            filters.add(predicate)
        }
    }

    private suspend fun logRequest(request: HttpRequest): OutgoingContent? {
        if (level.info) {
            logger.log("REQUEST: ${request.url}")
            logger.log("METHOD: ${request.method}")
        }
        if (level.headers) logHeaders(request.headers.entries(), request.content.headers)
        return if (level.body) {
            logRequestBody(request.content)
        } else null
    }

    private fun logResponse(response: HttpResponse) {
        if (level.info) {
            logger.log("RESPONSE: ${response.status}")
            logger.log("METHOD: ${response.call.request.method}")
            logger.log("FROM: ${response.call.request.url}")
        }

        if (level.headers) {
            logHeaders(response.headers.entries())
        }
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
        }

    }

    private fun logResponseException(context: HttpClientCall, cause: Throwable) {
        if (level.info) {
            logger.log("RESPONSE ${context.request.url} failed with exception: $cause")
        }
    }

    private fun logHeaders(
        requestHeaders: Set<Map.Entry<String, List<String>>>,
        contentHeaders: Headers? = null
    ) {
        with(logger) {
            log("COMMON HEADERS")
            requestHeaders.forEach { (key, values) ->
                log("-> $key: ${values.joinToString("; ")}")
            }

            contentHeaders ?: return@with

            log("CONTENT HEADERS")
            contentHeaders.forEach { key, values ->
                log("-> $key: ${values.joinToString("; ")}")
            }
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel) {
        with(logger) {
            log("BODY Content-Type: $contentType")
            log("BODY START")
            val message = content.readText(contentType?.charset() ?: Charsets.UTF_8)
            log(message)
            log("BODY END")
        }
    }

    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent? {
        with(logger) {
            log("BODY Content-Type: ${content.contentType}")

            val charset = content.contentType?.charset() ?: Charsets.UTF_8

            val channel = ByteChannel()
            GlobalScope.launch(Dispatchers.Unconfined) {
                val text = channel.readText(charset)
                log("BODY START")
                log(text)
                log("BODY END")
            }

            return content.observe(channel)
        }
    }

    companion object : HttpClientFeature<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level, config.filters, config.logContentType)
        }

        override fun install(feature: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                try {
                    proceed()
                } catch (cause: Throwable) {
                    feature.logRequestException(context, cause)
                    throw cause
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    if (feature.filters.isEmpty() || feature.filters.any { it(context) }) {
                        if (feature.logContentType.shouldLogRequest()) {
                            feature.logRequest(context.request)
                        }
                        if (feature.logContentType.shouldLogResponse()) {
                            feature.logResponse(context.response)
                        }
                    }
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    feature.logResponseException(context, cause)
                    throw cause
                }
            }

            if (!feature.level.body) {
                return
            }

            val observer: ResponseHandler = {
                try {
                    feature.logResponseBody(it.contentType(), it.content)
                } catch (_: Throwable) {
                }
            }

            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

private suspend inline fun ByteReadChannel.readText(charset: Charset): String =
    readRemaining().readText(charset = charset)

enum class LogContentType {
    REQUEST_ONLY,
    RESPONSE_ONLY,
    BOTH
}

fun LogContentType.shouldLogRequest() = this == LogContentType.BOTH || this == LogContentType.REQUEST_ONLY
fun LogContentType.shouldLogResponse() = this == LogContentType.BOTH || this == LogContentType.RESPONSE_ONLY
