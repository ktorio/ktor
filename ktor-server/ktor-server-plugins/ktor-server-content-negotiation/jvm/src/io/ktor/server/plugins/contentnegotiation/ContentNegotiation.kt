/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

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
 * This plugin provides automatic content conversion according to Content-Type and Accept headers
 *
 * See normative documents:
 *
 * * https://tools.ietf.org/html/rfc7231#section-5.3
 * * https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation
 */
public class ContentNegotiation internal constructor(
    internal val registrations: List<ConverterRegistration>,
    internal val acceptContributors: List<AcceptHeaderContributor>,
    private val checkAcceptHeaderCompliance: Boolean = false
) {

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
    internal class ConverterRegistration(val contentType: ContentType, val converter: ContentConverter)

    /**
     * Configuration type for [ContentNegotiation] plugin
     */
    public class Configuration : io.ktor.serialization.Configuration {
        internal val registrations = mutableListOf<ConverterRegistration>()
        internal val acceptContributors = mutableListOf<AcceptHeaderContributor>()

        /**
         * Checks that `ContentType` header value of the response suits `Accept` header value of the request
         */
        public var checkAcceptHeaderCompliance: Boolean = false

        /**
         * Registers a [contentType] to a specified [converter] with an optional [configuration] script for converter
         */
        public override fun <T : ContentConverter> register(
            contentType: ContentType,
            converter: T,
            configuration: T.() -> Unit
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
     * Implementation of an [ApplicationPlugin] for the [ContentNegotiation]
     */
    public companion object Plugin : RouteScopedPlugin<Configuration, ContentNegotiation> {
        override val key: AttributeKey<ContentNegotiation> = AttributeKey("ContentNegotiation")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): ContentNegotiation {
            val configuration = Configuration().apply(configure)
            val plugin = ContentNegotiation(
                configuration.registrations,
                configuration.acceptContributors,
                configuration.checkAcceptHeaderCompliance
            )

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) { subject ->
                if (subject is HttpStatusCodeContent) {
                    return@intercept
                }
                if (subject is OutgoingContent && !plugin.checkAcceptHeaderCompliance) {
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

                val acceptItems = plugin.acceptContributors.fold(acceptHeader) { acc, e ->
                    e(call, acc)
                }.distinct().sortedByQuality()

                val suitableConverters = if (acceptItems.isEmpty()) {
                    // all converters are suitable since client didn't indicate what it wants
                    plugin.registrations
                } else {
                    // select converters that match specified Accept header, in order of quality
                    acceptItems.flatMap { (contentType, _) ->
                        plugin.registrations.filter { it.contentType.match(contentType) }
                    }.distinct()
                }
                if (subject is OutgoingContent) {
                    if (!plugin.checkAcceptHeader(acceptItems, subject.contentType)) {
                        proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
                    }
                    return@intercept
                }

                // Pick the first one that can convert the subject successfully
                val converted = suitableConverters.firstNotNullOfOrNull {
                    it.converter.serialize(
                        contentType = it.contentType,
                        charset = call.request.headers.suitableCharset(),
                        typeInfo = call.response.responseType!!,
                        value = subject
                    )
                }

                val rendered = converted?.let { transformDefaultContent(it) }
                    ?: return@intercept

                val contentType = rendered.contentType
                if (plugin.checkAcceptHeader(acceptItems, contentType)) {
                    proceedWith(rendered)
                } else {
                    proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
                }
            }

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) { receive ->
                // skip if already transformed
                if (subject.value !is ByteReadChannel) return@intercept
                // skip if a byte channel has been requested so there is nothing to negotiate
                if (subject.typeInfo.type == ByteReadChannel::class) return@intercept

                val requestContentType = try {
                    call.request.contentType().withoutParameters()
                } catch (parseFailure: BadContentTypeFormatException) {
                    throw BadRequestException(
                        "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
                        parseFailure
                    )
                }
                val suitableConverters =
                    plugin.registrations.filter { converter -> requestContentType.match(converter.contentType) }
                        .takeIf { it.isNotEmpty() } ?: return@intercept

                val converted = try {
                    // Pick the first one that can convert the subject successfully
                    suitableConverters.firstNotNullOfOrNull { registration ->
                        registration.converter.deserialize(
                            charset = call.request.contentCharset() ?: Charsets.UTF_8,
                            typeInfo = subject.typeInfo,
                            content = subject.value as ByteReadChannel
                        )
                    } ?: return@intercept
                } catch (convertException: ContentConvertException) {
                    throw BadRequestException(
                        convertException.message ?: "Can't convert parameters",
                        convertException.cause
                    )
                }

                proceedWith(ApplicationReceiveRequest(receive.typeInfo, converted, reusableValue = true))
            }
            return plugin
        }
    }
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
