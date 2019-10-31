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
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.jvm.*

/**
 * [HttpClient] logging feature.
 *
 * @property logger to log call info to
 * @property level what should be logged
 */
class Logging(
    val logger: io.ktor.util.logging.Logger,
    var level: LogLevel,
    var filters: List<(HttpRequestBuilder) -> Boolean>
) {
    @Suppress("DEPRECATION")
    @Deprecated("Use ktor utils logger instead.")
    constructor(logger: Logger, level: LogLevel) : this(AdapterLogger(logger), level, emptyList())

    /**
     * [Logging] feature configuration
     */
    class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
        /**
         * [Logger] instance to use
         */
        @get:JvmName("getNewLogger")
        @set:JvmName("setNewLogger")
        var logger: io.ktor.util.logging.Logger = io.ktor.util.logging.Logger.Default

        @Suppress("KDocMissingDocumentation", "unused", "DEPRECATION")
        @get:JvmName("getLogger")
        @set:JvmName("setLogger")
        @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
        var deprecatedLogger: Logger
            get() = AdapterLogger(logger)
            set(newLogger) {
                logger = AdapterLogger(newLogger)
            }

        /**
         * log [LogLevel]
         */
        var level: LogLevel = LogLevel.HEADERS

        /**
         * Log messages for calls matching a [predicate]
         */
        fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    private suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        if (level.info) {
            logger.info("REQUEST: ${Url(request.url)}")
            logger.info("METHOD: ${request.method}")
        }
        val content = request.body as OutgoingContent
        if (level.headers) logHeaders(request.headers.entries(), content.headers)
        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    private fun logResponse(response: HttpResponse) {
        if (level.info) {
            logger.info("RESPONSE: ${response.status}")
            logger.info("METHOD: ${response.call.request.method}")
            logger.info("FROM: ${response.call.request.url}")
        }

        if (level.headers) {
            logHeaders(response.headers.entries())
        }
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.info("REQUEST ${Url(context.url)} failed with exception: $cause")
        }
    }

    private fun logResponseException(context: HttpClientCall, cause: Throwable) {
        if (level.info) {
            logger.info("RESPONSE ${context.request.url} failed with exception: $cause")
        }
    }

    private fun logHeaders(
        requestHeaders: Set<Map.Entry<String, List<String>>>,
        contentHeaders: Headers? = null
    ) {
        with(logger) {
            info("COMMON HEADERS")
            requestHeaders.forEach { (key, values) ->
                info("-> $key: ${values.joinToString("; ")}")
            }

            contentHeaders ?: return@with

            info("CONTENT HEADERS")
            contentHeaders.forEach { key, values ->
                info("-> $key: ${values.joinToString("; ")}")
            }
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel) {
        with(logger) {
            info("BODY Content-Type: $contentType")
            info("BODY START")
            val message = content.readText(contentType?.charset() ?: Charsets.UTF_8)
            info(message)
            info("BODY END")
        }
    }

    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent? {
        with(logger) {
            info("BODY Content-Type: ${content.contentType}")

            val charset = content.contentType?.charset() ?: Charsets.UTF_8

            val channel = ByteChannel()
            GlobalScope.launch(Dispatchers.Unconfined) {
                val text = channel.readText(charset)
                info("BODY START")
                info(text)
                info("BODY END")
            }

            return content.observe(channel)
        }
    }

    companion object : HttpClientFeature<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level, config.filters)
        }

        override fun install(feature: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = try {
                    if (feature.filters.isEmpty() || feature.filters.any { it(context) }) {
                        feature.logRequest(context)
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    null
                } ?: subject

                try {
                    proceedWith(response)
                } catch (cause: Throwable) {
                    feature.logRequestException(context, cause)
                    throw cause
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    feature.logResponse(context.response)
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
@Suppress("FunctionName")
fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

private suspend inline fun ByteReadChannel.readText(charset: Charset): String =
    readRemaining().readText(charset = charset)
