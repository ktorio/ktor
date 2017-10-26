package io.ktor.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

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

        private val contentTypeComparator = compareByDescending<Pair<ContentType, Double>> { it.second }.thenBy {
            val contentType = it.first
            var asterisks = 0
            if (contentType.contentType == "*")
                asterisks += 2
            if (contentType.contentSubtype == "*")
                asterisks++
            asterisks
        }.thenByDescending { it.first.parameters.size }

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
                if (subject is FinalContent) return@intercept

                val suitableConverters = acceptedTypes().mapNotNull { (contentType, _) ->
                    feature.converters.firstOrNull { it.contentType.match(contentType) }
                }

                val converted = suitableConverters.mapNotNull { it.converter.convertForSend(this, subject) }.firstOrNull()
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

        private fun PipelineContext<Any, ApplicationCall>.acceptedTypes(): List<Pair<ContentType, Double>> {
            val acceptHeader = call.request.header(HttpHeaders.Accept)
            val acceptHeaderValues = parseHeaderValue(acceptHeader)
            return acceptHeaderValues
                    .map { ContentType.parse(it.value) to it.quality }
                    .sortedWith(contentTypeComparator)
        }
    }
}

class UnsupportedMediaTypeException(contentType: ContentType) : Exception("Content type $contentType is not supported")

interface ContentConverter {
    suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, value: Any): Any?
    suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any?
}

class ConvertedContent(text: String, val contentType: ContentType) : FinalContent.ByteArrayContent() {
    private val bytes = text.toByteArray(Charsets.UTF_8)
    override fun bytes(): ByteArray = bytes

    override val headers = ValuesMap.build(true) {
        set(HttpHeaders.ContentType, contentType.toString())
        set(HttpHeaders.ContentLength, bytes.size.toString())
    }

    override fun toString() = "ConvertedContent($contentType) \"${bytes.toString(Charsets.UTF_8).take(30)}\""
}
