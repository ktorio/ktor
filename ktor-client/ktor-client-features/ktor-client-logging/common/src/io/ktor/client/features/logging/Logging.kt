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
import kotlinx.coroutines.sync.*

/**
 * [HttpClient] logging feature.
 */
public class Logging(
    public val logger: Logger,
    public var level: LogLevel,
    public var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {

    public constructor(logger: Logger, level: LogLevel) : this(logger, level, emptyList())

    private val mutex = Mutex()

    /**
     * [Logging] feature configuration
     */
    public class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

        /**
         * [Logger] instance to use
         */
        public var logger: Logger = Logger.DEFAULT

        /**
         * log [LogLevel]
         */
        public var level: LogLevel = LogLevel.HEADERS

        /**
         * Log messages for calls matching a [predicate]
         */
        public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    private suspend fun beginLogging() {
        mutex.lock()
    }

    private fun doneLogging() {
        mutex.unlock()
    }

    private suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        if (level.info) {
            logger.log("REQUEST: ${Url(request.url)}")
            logger.log("METHOD: ${request.method}")
        }

        val content = request.body as OutgoingContent

        if (level.headers) {
            logger.log("COMMON HEADERS")
            logHeaders(request.headers.entries())

            logger.log("CONTENT HEADERS")
            content.contentLength?.let { logger.logHeader(HttpHeaders.ContentLength, it.toString()) }
            content.contentType?.let { logger.logHeader(HttpHeaders.ContentType, it.toString()) }
            logHeaders(content.headers.entries())
        }

        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    private fun logResponse(response: HttpResponse) {
        if (level.info) {
            logger.log("RESPONSE: ${response.status}")
            logger.log("METHOD: ${response.call.request.method}")
            logger.log("FROM: ${response.call.request.url}")
        }

        if (level.headers) {
            logger.log("COMMON HEADERS")
            logHeaders(response.headers.entries())
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel): Unit = with(logger) {
        log("BODY Content-Type: $contentType")
        log("BODY START")
        val message = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "[response body omitted]"
        log(message)
        log("BODY END")
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

    private fun logHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        val sortedHeaders = headers.toList().sortedBy { it.key }

        sortedHeaders.forEach { (key, values) ->
            logger.logHeader(key, values.joinToString("; "))
        }
    }

    private fun Logger.logHeader(key: String, value: String) {
        log("-> $key: $value")
    }

    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent? {
        logger.log("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            val text = channel.tryReadText(charset) ?: "[request body omitted]"
            logger.log("BODY START")
            logger.log(text)
            logger.log("BODY END")
        }

        return content.observe(channel)
    }

    public companion object : HttpClientFeature<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level, config.filters)
        }

        override fun install(feature: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = if (feature.filters.isEmpty() || feature.filters.any { it(context) }) {
                    try {
                        feature.beginLogging()
                        feature.logRequest(context)
                    } catch (_: Throwable) {
                        null
                    } finally {
                        feature.doneLogging()
                    }
                } else null

                try {
                    proceedWith(response ?: subject)
                } catch (cause: Throwable) {
                    feature.logRequestException(context, cause)
                    throw cause
                } finally {
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) {
                try {
                    feature.beginLogging()
                    feature.logResponse(context.response)
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    feature.logResponseException(context, cause)
                    throw cause
                } finally {
                    if (!feature.level.body) {
                        feature.doneLogging()
                    }
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    proceed()
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
                } finally {
                    feature.doneLogging()
                }
            }

            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
public fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}
