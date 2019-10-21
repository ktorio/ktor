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
    var level: LogLevel
) {
    /**
     * [Logging] feature configuration
     */
    class Config {
        /**
         * [Logger] instance to use
         */
        var logger: Logger = Logger.DEFAULT

        /**
         * log [LogLevel]
         */
        var level: LogLevel = LogLevel.HEADERS
    }

    private suspend fun logRequest(request: HttpRequestBuilder) {
        if (level.info) {
            logger.log("REQUEST: ${Url(request.url)}")
            logger.log("METHOD: ${request.method}")
        }
        val content = request.body as OutgoingContent
        if (level.headers) logHeaders(request.headers.entries(), content.headers)
        if (level.body) logRequestBody(content)
    }

    private suspend fun logResponse(response: HttpResponse) {
        if (level.info) {
            logger.log("RESPONSE: ${response.status}")
            logger.log("METHOD: ${response.call.request.method}")
            logger.log("FROM: ${response.call.request.url}")
        }

        if (level.headers) logHeaders(response.headers.entries())
    }

    private fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (!level.info) return
        logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
    }

    private fun logResponseException(context: HttpClientCall, cause: Throwable) {
        if (!level.info) return
        logger.log("RESPONSE ${context.request.url} failed with exception: $cause")
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

    private suspend fun logRequestBody(content: OutgoingContent) {
        with(logger) {
            log("BODY Content-Type: ${content.contentType}")

            val charset = content.contentType?.charset() ?: Charsets.UTF_8

            val text = when (content) {
                is OutgoingContent.WriteChannelContent -> {
                    val textChannel = ByteChannel()
                    GlobalScope.launch(Dispatchers.Unconfined) {
                        content.writeTo(textChannel)
                        textChannel.close()
                    }
                    textChannel.readText(charset)
                }
                is OutgoingContent.ReadChannelContent -> {
                    content.readFrom().readText(charset)
                }
                is OutgoingContent.ByteArrayContent -> String(content.bytes(), charset = charset)
                else -> null
            }
            log("BODY START")
            text?.let { log(it) }
            log("BODY END")
        }
    }

    companion object : HttpClientFeature<Config, Logging> {
        override val key: AttributeKey<Logging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): Logging {
            val config = Config().apply(block)
            return Logging(config.logger, config.level)
        }

        override fun install(feature: Logging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Before) {
                try {
                    feature.logRequest(context)
                } catch (_: Throwable) {

                }

                try {
                    proceedWith(subject)
                } catch (cause: Throwable) {
                    feature.logRequestException(context, cause)
                    throw cause
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                try {
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
