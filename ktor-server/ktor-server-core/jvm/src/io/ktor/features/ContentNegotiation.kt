/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.text.Charsets

/**
 * Functional type for accepted content types contributor
 * @see ContentNegotiation.Configuration.accept
 */
public typealias AcceptHeaderContributor = (
    call: ApplicationCall,
    acceptedContentTypes: List<ContentTypeWithQuality>
) -> List<ContentTypeWithQuality>

/**
 * Pair of [ContentType] and [quality] usually parsed from [HttpHeaders.Accept] headers.
 * @param contentType
 * @param quality
 */
public data class ContentTypeWithQuality(val contentType: ContentType, val quality: Double = 1.0) {
    init {
        require(quality in 0.0..1.0) { "Quality should be in range [0, 1]: $quality" }
    }
}

/**
 * This feature provides automatic content conversion according to Content-Type and Accept headers
 *
 * See normative documents:
 *
 * * https://tools.ietf.org/html/rfc7231#section-5.3
 * * https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation
 *
 * @param registrations is a list of registered converters for ContentTypes
 */
public class ContentNegotiation internal constructor(
    public val registrations: List<ConverterRegistration>,
    private val acceptContributors: List<AcceptHeaderContributor>,
    private val checkAcceptHeaderCompliance: Boolean = false
) {

    @Deprecated("This will become internal", level = DeprecationLevel.HIDDEN)
    public constructor(
        registrations: List<ConverterRegistration>,
        acceptContributors: List<AcceptHeaderContributor>
    ) : this(registrations, acceptContributors, false)

    internal fun checkAcceptHeader(
        acceptItems: List<ContentTypeWithQuality>,
        contentType: ContentType?
    ): Boolean {
        if (!checkAcceptHeaderCompliance) {
            return true
        }
        if (acceptItems.isEmpty()) {
            return true
        }
        if (contentType == null) {
            return true
        }
        return acceptItems.any { contentType.match(it.contentType) }
    }

    /**
     * Specifies which [converter] to use for a particular [contentType]
     * @param contentType is an instance of [ContentType] for this registration
     * @param converter is an instance of [ContentConverter] for this registration
     */
    public data class ConverterRegistration(
        val contentType: ContentType,
        val converter: ContentConverter
    )

    /**
     * Configuration type for [ContentNegotiation] feature
     */
    public class Configuration {
        internal val registrations = mutableListOf<ConverterRegistration>()
        internal val acceptContributors = mutableListOf<AcceptHeaderContributor>()

        /**
         * Checks that `ContentType` header value of the response suits `Accept` header value of the request
         */
        public var checkAcceptHeaderCompliance: Boolean = false

        /**
         * Registers a [contentType] to a specified [converter] with an optional [configuration] script for converter
         */
        public fun <T : ContentConverter> register(
            contentType: ContentType,
            converter: T,
            configuration: T.() -> Unit = {}
        ) {
            val registration = ConverterRegistration(contentType, converter.apply(configuration))
            registrations.add(registration)
        }

        /**
         * Register a custom accepted content types [contributor]. A [contributor] function takes [ApplicationCall]
         * and a list of content types accepted according to [HttpHeaders.Accept] header or provided by the previous
         * contributor if exists. Result of this [contributor] should be a list of accepted content types
         * with quality. A [contributor] could either keep or replace input list of accepted content types depending
         * on use-case. For example a contributor taking `format=json` request parameter could replace the original
         * content types list with the specified one from the uri argument.
         * Note that the returned list of accepted types will be sorted according to quality using [sortedByQuality]
         * so a custom [contributor] may keep it unsorted and should not rely on input list order.
         */
        public fun accept(contributor: AcceptHeaderContributor) {
            acceptContributors.add(contributor)
        }
    }

    /**
     * Implementation of an [ApplicationFeature] for the [ContentNegotiation]
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ContentNegotiation> {
        override val key: AttributeKey<ContentNegotiation> = AttributeKey("ContentNegotiation")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): ContentNegotiation {
            val configuration = Configuration().apply(configure)
            val feature = ContentNegotiation(
                configuration.registrations,
                configuration.acceptContributors,
                configuration.checkAcceptHeaderCompliance
            )

            // Respond with "415 Unsupported Media Type" if content cannot be transformed on receive
            pipeline.intercept(ApplicationCallPipeline.Features) {
                try {
                    proceed()
                } catch (e: UnsupportedMediaTypeException) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) { subject ->
                if (subject is HttpStatusCodeContent) {
                    return@intercept
                }
                if (subject is OutgoingContent && !feature.checkAcceptHeaderCompliance) {
                    return@intercept
                }

                val acceptHeaderContent = call.request.header(HttpHeaders.Accept)
                val acceptHeader = try {
                    parseHeaderValue(acceptHeaderContent)
                        .map { ContentTypeWithQuality(ContentType.parse(it.value), it.quality) }
                } catch (parseFailure: BadContentTypeFormatException) {
                    throw BadRequestException(
                        "Illegal Accept header format: $acceptHeaderContent",
                        parseFailure
                    )
                }

                val acceptItems = feature.acceptContributors.fold(acceptHeader) { acc, e ->
                    e(call, acc)
                }.distinct().sortedByQuality()

                val suitableConverters = if (acceptItems.isEmpty()) {
                    // all converters are suitable since client didn't indicate what it wants
                    feature.registrations
                } else {
                    // select converters that match specified Accept header, in order of quality
                    acceptItems.flatMap { (contentType, _) ->
                        feature.registrations.filter { it.contentType.match(contentType) }
                    }.distinct()
                }
                if (subject is OutgoingContent) {
                    if (!feature.checkAcceptHeader(acceptItems, subject.contentType)) {
                        proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
                    }
                    return@intercept
                }

                // Pick the first one that can convert the subject successfully
                val converted = suitableConverters.mapFirstNotNull {
                    it.converter.convertForSend(this, it.contentType, subject)
                }

                val rendered = converted?.let { transformDefaultContent(it) }
                    ?: HttpStatusCodeContent(HttpStatusCode.NotAcceptable)

                val contentType = rendered.contentType
                if (feature.checkAcceptHeader(acceptItems, contentType)) {
                    proceedWith(rendered)
                } else {
                    proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
                }
            }

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) { receive ->
                // skip if already transformed
                if (subject.value !is ByteReadChannel) return@intercept
                // skip if a byte channel has been requested so there is nothing to negotiate
                if (subject.type == ByteReadChannel::class) return@intercept

                val requestContentType = try {
                    call.request.contentType().withoutParameters()
                } catch (parseFailure: BadContentTypeFormatException) {
                    throw BadRequestException(
                        "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
                        parseFailure
                    )
                }
                val suitableConverter =
                    feature.registrations.firstOrNull { converter -> requestContentType.match(converter.contentType) }
                        ?: throw UnsupportedMediaTypeException(requestContentType)

                val converted = suitableConverter.converter.convertForReceive(this)
                    ?: throw UnsupportedMediaTypeException(requestContentType)

                proceedWith(ApplicationReceiveRequest(receive.typeInfo, converted, reusableValue = true))
            }
            return feature
        }
    }
}

/**
 * A custom content converted that could be registered in [ContentNegotiation] feature for any particular content type
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of content converter is a
 * json content converter that provides both serialization and deserialization
 */
public interface ContentConverter {
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
     * @return a converted value (possibly an [OutgoingContent]), or null if [value] isn't suitable for this converter
     */
    public suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any?

    /**
     * Convert a value (RAW or intermediate) from receive pipeline (deserialize).
     * Pipeline [PipelineContext.subject] has [ApplicationReceiveRequest.value] of type [ByteReadChannel]
     *
     * @return a converted value (deserialized) or `null` if the context's subject is not suitable for this converter
     */
    public suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any?
}

/**
 * Detect suitable charset for an application call by `Accept` header or fallback to [defaultCharset]
 */
public fun ApplicationCall.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in request.acceptCharsetItems()) when {
        charset == "*" -> return defaultCharset
        Charset.isSupported(charset) -> return Charset.forName(charset)
    }
    return defaultCharset
}

/**
 * Returns a list of content types sorted by quality, number of asterisks and number of parameters.
 * @see parseAndSortContentTypeHeader
 */
public fun List<ContentTypeWithQuality>.sortedByQuality(): List<ContentTypeWithQuality> {
    return sortedWith(
        compareByDescending<ContentTypeWithQuality> { it.quality }.thenBy {
            val contentType = it.contentType
            var asterisks = 0
            if (contentType.contentType == "*") {
                asterisks += 2
            }
            if (contentType.contentSubtype == "*") {
                asterisks++
            }
            asterisks
        }.thenByDescending { it.contentType.parameters.size }
    )
}

private inline fun <F, T> Iterable<F>.mapFirstNotNull(block: (F) -> T?): T? {
    @Suppress("LoopToCallChain")
    for (element in this) {
        val mapped = block(element)
        if (mapped != null) {
            return mapped
        }
    }
    return null
}
