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

private val HttpResponseLog = AttributeKey<StringBuilder>("HttpResponseLog")

private val DisableLogging = AttributeKey<Unit>("DisableLogging")

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
         * Log messages for calls matching a [predicate].
         */
        public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    private fun setupRequestLogging(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            if (!shouldBeLogged(context)) {
                context.attributes.put(DisableLogging, Unit)
                return@intercept
            }

            val response = try {
                logRequest(context)
            } catch (_: Throwable) {
                null
            }

            try {
                proceedWith(response ?: subject)
            } catch (cause: Throwable) {
                logRequestException(context, cause)
                throw cause
            } finally {
            }
        }
    }

    private fun shouldBeLogged(request: HttpRequestBuilder): Boolean = filters.isEmpty() || filters.any { it(request) }

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

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
        }
    }

    @OptIn(InternalAPI::class)
    private fun setupResponseLogging(client: HttpClient) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
            if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) {
                return@intercept
            }

            val log = StringBuilder()
            if (level.body) {
                response.call.attributes.put(HttpResponseLog, log)
            }

            try {
                logResponseHeader(log, response.call.response)
                proceedWith(subject)

                if (!level.body) {
                    logger.log(log.toString().trim())
                }
            } catch (cause: Throwable) {
                logger.log(log.toString().trim())
                logResponseException(response.call.request, cause)
                throw cause
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            if (level == LogLevel.NONE || context.attributes.contains(DisableLogging)) {
                return@intercept
            }

            try {
                proceed()
            } catch (cause: Throwable) {
                logResponseException(context.request, cause)
                throw cause
            }
        }

        if (!level.body) return

        val observer: ResponseHandler = observer@{
            if (level == LogLevel.NONE || it.call.attributes.contains(DisableLogging)) {
                return@observer
            }

            val log = it.call.attributes[HttpResponseLog]
            try {
                logResponseBody(log, it.contentType(), it.content)
            } catch (_: Throwable) {
            } finally {
                logger.log(log.toString().trim())
            }
        }

        ResponseObserver.install(ResponseObserver(observer), client)
    }

    private fun logResponseHeader(log: StringBuilder, response: HttpResponse) {
        with(log) {
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
    }

    private suspend fun logResponseBody(
        log: StringBuilder,
        contentType: ContentType?,
        content: ByteReadChannel
    ) {
        with(log) {
            appendLine("BODY Content-Type: $contentType")
            appendLine("BODY START")

            val message = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "[response body omitted]"
            appendLine(message)
            append("BODY END")
        }
    }

    private fun logResponseException(request: HttpRequest, cause: Throwable) {
        if (level.info) {
            logger.log("RESPONSE ${request.url} failed with exception: $cause")
        }
    }

    public companion object : HttpClientPlugin<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level, config.filters)
        }

        override fun install(plugin: Logging, scope: HttpClient) {
            plugin.setupRequestLogging(scope)
            plugin.setupResponseLogging(scope)
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
