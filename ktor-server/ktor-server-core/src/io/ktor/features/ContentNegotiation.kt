package io.ktor.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.nio.charset.Charset

/**
 * https://tools.ietf.org/html/rfc7231#section-5.3
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation
 */
class ContentNegotiation(val converters: List<ContentConverterRegistration>) {
    data class ContentConverterRegistration(val contentType: ContentType, val converter: ContentConverter)

    class Configuration {
        internal val converters = ArrayList<ContentConverterRegistration>()

        fun <T : ContentConverter> register(contentType: ContentType, converter: T, configure: T.() -> Unit = {}) {
            val registration = ContentConverterRegistration(contentType, converter.apply(configure))
            converters.add(registration)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ContentNegotiation> {
        override val key = AttributeKey<ContentNegotiation>("gson")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ContentNegotiation {
            val configuration = Configuration().apply(configure)
            val feature = ContentNegotiation(configuration.converters)

            // Respond with "415 Unsupported Media Type" if content cannot be transformed on receive
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
                try {
                    proceed()
                } catch (e: UnsupportedMediaTypeException) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
                if (subject is OutgoingContent) return@intercept

                val suitableConverters = call.request.acceptItems().mapNotNull { (contentType, _) ->
                    feature.converters.firstOrNull { it.contentType.match(contentType) }
                }

                val converted = suitableConverters.mapContent(this, subject)
                val rendered = converted?.let { transformDefaultContent(it) } ?: HttpStatusCodeContent(HttpStatusCode.NotAcceptable)
                proceedWith(rendered)
            }

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                if (subject.value !is IncomingContent) return@intercept
                val contentType = call.request.contentType().withoutParameters()
                val suitableConverter = feature.converters.firstOrNull { it.contentType.match(contentType) }
                        ?: throw UnsupportedMediaTypeException(contentType)
                val converted = suitableConverter.converter.convertForReceive(this)
                        ?: throw UnsupportedMediaTypeException(contentType)
                proceedWith(ApplicationReceiveRequest(it.type, converted))
            }
            return feature
        }
    }
}

class UnsupportedMediaTypeException(contentType: ContentType) : Exception("Content type $contentType is not supported")

interface ContentConverter {
    suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any?
    suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any?
}

fun PipelineContext<Any, ApplicationCall>.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in call.request.acceptCharsetItems()) when {
        charset == "*" -> return defaultCharset
        Charset.isSupported(charset) -> return Charset.forName(charset)
    }
    return defaultCharset
}

private suspend fun List<ContentNegotiation.ContentConverterRegistration>.mapContent(context: PipelineContext<Any, ApplicationCall>, value: Any): Any? {
    forEach { it.converter.convertForSend(context, it.contentType, value)?.also { return it } }
    return null
}
