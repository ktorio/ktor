/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * A plugin that serves two primary purposes:
 * - Negotiating media types between the client and server. For this, it uses the `Accept` and `Content-Type` headers.
 * - Serializing/deserializing the content in a specific format when sending requests and receiving responses.
 *    Ktor supports the following formats out-of-the-box: `JSON`, `XML`, and `CBOR`.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization-client.html).
 */
public class ContentNegotiation internal constructor(
    internal val registrations: List<Config.ConverterRegistration>
) {

    /**
     * A [ContentNegotiation] configuration that is used during installation.
     */
    public class Config : Configuration {

        internal class ConverterRegistration(
            val converter: ContentConverter,
            val contentTypeToSend: ContentType,
            val contentTypeMatcher: ContentTypeMatcher,
        )

        internal val registrations = mutableListOf<ConverterRegistration>()

        /**
         * Registers a [contentType] to a specified [converter] with an optional [configuration] script for a converter.
         */
        public override fun <T : ContentConverter> register(
            contentType: ContentType,
            converter: T,
            configuration: T.() -> Unit
        ) {
            val matcher = when (contentType) {
                ContentType.Application.Json -> JsonContentTypeMatcher
                else -> defaultMatcher(contentType)
            }
            register(contentType, converter, matcher, configuration)
        }

        /**
         * Registers a [contentTypeToSend] and [contentTypeMatcher] to a specified [converter] with
         * an optional [configuration] script for a converter.
         */
        public fun <T : ContentConverter> register(
            contentTypeToSend: ContentType,
            converter: T,
            contentTypeMatcher: ContentTypeMatcher,
            configuration: T.() -> Unit
        ) {
            val registration = ConverterRegistration(
                converter.apply(configuration),
                contentTypeToSend,
                contentTypeMatcher
            )
            registrations.add(registration)
        }

        private fun defaultMatcher(pattern: ContentType): ContentTypeMatcher = object : ContentTypeMatcher {
            override fun contains(contentType: ContentType): Boolean = contentType.match(pattern)
        }
    }

    /**
     * A companion object used to install a plugin.
     */
    @KtorDsl
    public companion object Plugin : HttpClientPlugin<Config, ContentNegotiation> {
        public override val key: AttributeKey<ContentNegotiation> = AttributeKey("ContentNegotiation")

        override fun prepare(block: Config.() -> Unit): ContentNegotiation {
            val config = Config().apply(block)
            return ContentNegotiation(config.registrations)
        }

        override fun install(plugin: ContentNegotiation, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                val registrations = plugin.registrations
                registrations.forEach { context.accept(it.contentTypeToSend) }

                val contentType = context.contentType() ?: return@intercept

                if (payload is Unit || payload is EmptyContent) {
                    context.headers.remove(HttpHeaders.ContentType)
                    proceedWith(EmptyContent)
                    return@intercept
                }

                val matchingRegistrations = registrations.filter { it.contentTypeMatcher.contains(contentType) }
                    .takeIf { it.isNotEmpty() } ?: return@intercept
                if (context.bodyType == null) return@intercept
                context.headers.remove(HttpHeaders.ContentType)

                // Pick the first one that can convert the subject successfully
                val serializedContent = matchingRegistrations.firstNotNullOfOrNull { registration ->
                    registration.converter.serialize(
                        contentType,
                        contentType.charset() ?: Charsets.UTF_8,
                        context.bodyType!!,
                        payload
                    )
                } ?: throw ContentConverterException(
                    "Can't convert $payload with contentType $contentType using converters " +
                        matchingRegistrations.joinToString { it.converter.toString() }
                )

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept
                if (info.type == ByteReadChannel::class) return@intercept

                val contentType = context.response.contentType() ?: return@intercept
                val registrations = plugin.registrations
                val matchingRegistrations = registrations
                    .filter { it.contentTypeMatcher.contains(contentType) }
                    .takeIf { it.isNotEmpty() } ?: return@intercept

                // Pick the first one that can convert the subject successfully
                val parsedBody = matchingRegistrations.firstNotNullOfOrNull { registration ->
                    registration.converter
                        .deserialize(context.request.headers.suitableCharset(), info, body)
                } ?: return@intercept
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

public class ContentConverterException(message: String) : Exception(message)
