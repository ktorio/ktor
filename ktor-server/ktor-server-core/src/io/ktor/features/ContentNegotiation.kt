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

                val converted = suitableConverters.mapUntilNotNull {
                    it.converter.convertForSend(this, it.contentType, subject)
                }

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

/**
 * A custom content converted that could be registered in [ContentNegotiation] feature for any particular content type
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of content converter is a json content converter that provides both serialziation
 * and deserialization
 */
interface ContentConverter {
    /**
     * Convert a [value] to the specified [contentType] to a value suitable for sending (serialize).
     * Note that as far as [ContentConverter] could be registered multiple times with different content types
     * hence [contentType] could be different depends on what the client accepts (inferred from Accept header).
     * This function could ignore value if it is not suitable for conversion and return `null` so in this case
     * other registered converters could be tried or this function could be invoked with other content types
     * it the converted has been registered multiple times with different content types
     *
     * @param context pipeline context
     * @param contentType to which this data converted has been registered and that matches client's accept header
     * @param value to be converted
     *
     * @return a converted value of null if this [value] is not suitable for this converter
     */
    suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any?

    /**
     * Convert a value (RAW or intermediate) from receive pipeline (deserialize)
     *
     * @return a converted value (deserialized) or `null` if the context's subject is not suitable for this converter
     */
    suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any?
}

fun ApplicationCall.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in request.acceptCharsetItems()) when {
        charset == "*" -> return defaultCharset
        Charset.isSupported(charset) -> return Charset.forName(charset)
    }
    return defaultCharset
}

private inline fun <F, T> Iterable<F>.mapUntilNotNull(block: (F) -> T?): T? {
    @Suppress("LoopToCallChain")
    for (element in this) {
        val mapped = block(element)
        if (mapped != null) return mapped
    }

    return null
}