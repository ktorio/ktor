/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.observer.*
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
 * A client's logging plugin.
 */
public class Logging private constructor(
    public val logger: Logger,
    public var level: LogLevel,
    public var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {

    /**
     * [Logging] plugin configuration
     */
    @KtorDsl
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

    private suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        val content = request.body as OutgoingContent
        val message = buildString {
            if (level.info) {
                appendLine("REQUEST: ${Url(request.url)}")
                appendLine("METHOD: ${request.method}")
            }

            if (level.headers) {
                appendLine("COMMON HEADERS")
                logHeaders(request.headers.entries())

                appendLine("CONTENT HEADERS")
                content.contentLength?.let { logHeader(HttpHeaders.ContentLength, it.toString()) }
                content.contentType?.let { logHeader(HttpHeaders.ContentType, it.toString()) }
                logHeaders(content.headers.entries())
            }
        }
        if (message.isNotEmpty()) {
            logger.log(message.trim())
        }
        return if (level.body) {
            logRequestBody(content)
        } else null
    }

    private fun logResponse(response: HttpResponse) {
        val message = buildString {
            if (level.info) {
                appendLine("RESPONSE: ${response.status}")
                appendLine("METHOD: ${response.call.request.method}")
                appendLine("FROM: ${response.call.request.url}")
            }

            if (level.headers) {
                appendLine("COMMON HEADERS")
                logHeaders(response.headers.entries())
            }
        }
        if (message.isNotEmpty()) {
            logger.log(message.trim())
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel) {
        val message = buildString {
            appendLine("BODY Content-Type: $contentType")
            appendLine("BODY START")
            val message = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "[response body omitted]"
            appendLine(message)
            append("BODY END")
        }
        logger.log(message)
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
        }
    }

    private fun logResponseException(request: HttpRequest, cause: Throwable) {
        if (level.info) {
            logger.log("RESPONSE ${request.url} failed with exception: $cause")
        }
    }

    private fun Appendable.logHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        val sortedHeaders = headers.toList().sortedBy { it.key }

        sortedHeaders.forEach { (key, values) ->
            logHeader(key, values.joinToString("; "))
        }
    }

    private fun Appendable.logHeader(key: String, value: String) {
        appendLine("-> $key: $value")
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun logRequestBody(content: OutgoingContent): OutgoingContent {
        val bodyLog = StringBuilder()
        bodyLog.appendLine("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            val text = channel.tryReadText(charset) ?: "[request body omitted]"
            bodyLog.appendLine("BODY START")
            bodyLog.appendLine(text)
            bodyLog.append("BODY END")
            logger.log(bodyLog.toString())
        }
        return content.observe(channel)
    }

    public companion object : HttpClientPlugin<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level, config.filters)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                val response = if (plugin.filters.isEmpty() || plugin.filters.any { it(context) }) {
                    try {
                        plugin.logRequest(context)
                    } catch (_: Throwable) {
                        null
                    }
                } else null

                try {
                    proceedWith(response ?: subject)
                } catch (cause: Throwable) {
                    plugin.logRequestException(context, cause)
                    throw cause
                } finally {
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                try {
                    plugin.logResponse(response.call.response)
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    plugin.logResponseException(response.call.request, cause)
                    throw cause
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
                    proceed()
                } catch (cause: Throwable) {
                    plugin.logResponseException(context.request, cause)
                    throw cause
                }
            }

            if (!plugin.level.body) {
                return
            }

            val observer: ResponseHandler = {
                try {
                    plugin.logResponseBody(it.contentType(), it.content)
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
public fun HttpClientConfig<*>.Logging(block: Logging.Config.() -> Unit = {}) {
    install(Logging, block)
}

internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}
