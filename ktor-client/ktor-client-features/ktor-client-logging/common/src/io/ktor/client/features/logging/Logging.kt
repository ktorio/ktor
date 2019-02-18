package io.ktor.client.features.logging

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

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

    private suspend fun logResponse(response: HttpResponse): Unit = response.use {
        if (level.info) {
            logger.log("RESPONSE: ${response.status}")
            logger.log("METHOD: ${response.call.request.method}")
            logger.log("FROM: ${response.call.request.url}")
        }

        if (level.headers) logHeaders(response.headers.entries())
        if (level.body) {
            logResponseBody(response.contentType(), response.content)
        } else {
            response.content.discard()
        }
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

            val observer: ResponseHandler = {
                try {
                    feature.logResponse(it)
                } catch (_: Throwable) {
                }
            }

            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }


}

private suspend inline fun ByteReadChannel.readText(charset: Charset): String {
    val packet = readRemaining(Long.MAX_VALUE, 0)
    return packet.readText(charset = charset)
}
