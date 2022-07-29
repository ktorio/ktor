/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.reflect.*

internal val DefaultCommonIgnoredTypes: Set<KClass<*>> = setOf(
    ByteArray::class,
    String::class,
    HttpStatusCode::class,
    ByteReadChannel::class,
    OutgoingContent::class
)

internal expect val DefaultIgnoredTypes: Set<KClass<*>>

/**
 * A plugin that serves two primary purposes:
 * - Negotiating media types between the client and server. For this, it uses the `Accept` and `Content-Type` headers.
 * - Serializing/deserializing the content in a specific format when sending requests and receiving responses.
 *    Ktor supports the following formats out-of-the-box: `JSON`, `XML`, and `CBOR`.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization-client.html).
 */
public class ContentNegotiation internal constructor(
    internal val registrations: List<Config.ConverterRegistration>,
    internal val ignoredTypes: Set<KClass<*>>
) {

    /**
     * A [ContentNegotiation] configuration that is used during installation.
     */
    public class Config : Configuration {

        internal class ConverterRegistration(
            val converter: ContentConverter,
            val contentTypeToSend: ContentType,
            val contentTypeMatcher: ContentTypeMatcher
        )

        @PublishedApi
        internal val ignoredTypes: MutableSet<KClass<*>> =
            (DefaultIgnoredTypes + DefaultCommonIgnoredTypes).toMutableSet()

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

        /**
         * Adds a type to the list of types that should be ignored by [ContentNegotiation].
         *
         * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
         */
        public inline fun <reified T> ignoreType() {
            ignoredTypes.add(T::class)
        }

        /**
         * Remove [T] from the list of types that should be ignored by [ContentNegotiation].
         */
        public inline fun <reified T> removeIgnoredType() {
            ignoredTypes.remove(T::class)
        }

        /**
         * Clear all configured ignored types including defaults.
         */
        public fun clearIgnoredTypes() {
            ignoredTypes.clear()
        }

        private fun defaultMatcher(pattern: ContentType): ContentTypeMatcher = object : ContentTypeMatcher {
            override fun contains(contentType: ContentType): Boolean = contentType.match(pattern)
        }
    }

    internal suspend fun convertRequest(request: HttpRequestBuilder, body: Any): Any? {
        registrations.forEach { request.accept(it.contentTypeToSend) }

        if (body is OutgoingContent || ignoredTypes.any { it.isInstance(body) }) return null
        val contentType = request.contentType() ?: return null

        if (body is Unit) {
            request.headers.remove(HttpHeaders.ContentType)
            return EmptyContent
        }

        val matchingRegistrations = registrations.filter { it.contentTypeMatcher.contains(contentType) }
            .takeIf { it.isNotEmpty() } ?: return null
        if (request.bodyType == null) return null
        request.headers.remove(HttpHeaders.ContentType)

        // Pick the first one that can convert the subject successfully
        val serializedContent = matchingRegistrations.firstNotNullOfOrNull { registration ->
            registration.converter.serializeNullable(
                contentType,
                contentType.charset() ?: Charsets.UTF_8,
                request.bodyType!!,
                body.takeIf { it != NullBody }
            )
        } ?: throw ContentConverterException(
            "Can't convert $body with contentType $contentType using converters " +
                matchingRegistrations.joinToString { it.converter.toString() }
        )

        return serializedContent
    }

    @OptIn(InternalAPI::class)
    internal suspend fun convertResponse(
        info: TypeInfo,
        body: Any,
        responseContentType: ContentType,
        charset: Charset = Charsets.UTF_8
    ): Any? {
        if (body !is ByteReadChannel) return null
        if (info.type in ignoredTypes) return null

        val suitableConverters = registrations
            .filter { it.contentTypeMatcher.contains(responseContentType) }
            .map { it.converter }
            .takeIf { it.isNotEmpty() } ?: return null

        return suitableConverters.deserialize(body, info, charset)
    }

    /**
     * A companion object used to install a plugin.
     */
    @KtorDsl
    public companion object Plugin : HttpClientPlugin<Config, ContentNegotiation> {
        public override val key: AttributeKey<ContentNegotiation> = AttributeKey("ContentNegotiation")

        override fun prepare(block: Config.() -> Unit): ContentNegotiation {
            val config = Config().apply(block)
            return ContentNegotiation(config.registrations, config.ignoredTypes)
        }

        override fun install(plugin: ContentNegotiation, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) {
                val result = plugin.convertRequest(context, subject) ?: return@intercept
                proceedWith(result)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                val contentType = context.response.contentType() ?: return@intercept
                val charset = context.request.headers.suitableCharset()

                val deserializedBody = plugin.convertResponse(info, body, contentType, charset) ?: return@intercept
                val result = HttpResponseContainer(info, deserializedBody)
                proceedWith(result)
            }
        }
    }
}

public class ContentConverterException(message: String) : Exception(message)
