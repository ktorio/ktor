package io.ktor.client.features.logging

import io.ktor.client.*
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
            logger.log("REQUEST: ${request.url.buildString()}")
            logger.log("METHOD: ${request.method}")
        }
        if (level.headers) logHeaders(request.headers.entries())
        if (level.body) logRequestBody(request.body as OutgoingContent)
    }

    private suspend fun logResponse(response: HttpResponse) {
        if (level == LogLevel.NONE) return

        val info = buildString {
            append("RESPONSE: ${response.status}\n")
            append("METHOD: ${response.call.request.method}\n")
            append("FROM: ${response.call.request.url}")
        }

        logger.log(info)

        if (level.headers) logHeaders(response.headers.entries())
        if (level.body) logResponseBody(response.contentType(), response.content)
    }

    private fun logHeaders(headersMap: Set<Map.Entry<String, List<String>>>) {
        with(logger) {
            log("HEADERS")

            headersMap.forEach { (key, values) ->
                log("-> $key: ${values.joinToString("; ")}")
            }
        }
    }

    private suspend fun logResponseBody(contentType: ContentType?, content: ByteReadChannel) {
        with(logger) {
            log("BODY Content-Type: $contentType")
            log("BODY START")
            log(content.readText(contentType?.charset() ?: Charsets.UTF_8))
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
                feature.logRequest(context)
            }

            val observer: ResponseHandler = {
                feature.logResponse(it)
            }

            ResponseObserver.install(ResponseObserver(observer), scope)
        }
    }
}

private suspend inline fun ByteReadChannel.readText(charset: Charset): String =
    readRemaining().readText(charset = charset)
