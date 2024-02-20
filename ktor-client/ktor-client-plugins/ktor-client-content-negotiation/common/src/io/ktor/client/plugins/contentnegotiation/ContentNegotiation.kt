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
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.reflect.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.contentnegotiation.ContentNegotiation")

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
            ignoreType(T::class)
        }

        /**
         * Remove [T] from the list of types that should be ignored by [ContentNegotiation].
         */
        public inline fun <reified T> removeIgnoredType() {
            removeIgnoredType(T::class)
        }

        /**
         * Remove [type] from the list of types that should be ignored by [ContentNegotiation].
         */
        public fun removeIgnoredType(type: KClass<*>) {
            ignoredTypes.remove(type)
        }

        /**
         * Adds a [type] to the list of types that should be ignored by [ContentNegotiation].
         *
         * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
         */
        public fun ignoreType(type: KClass<*>) {
            ignoredTypes.add(type)
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
        registrations.forEach {
            LOGGER.trace("Adding Accept=${it.contentTypeToSend.contentType} header for ${request.url}")

            if (request.headers.contains(HttpHeaders.Accept, it.contentTypeToSend.toString())) return@forEach
            request.accept(it.contentTypeToSend)
        }

        if (body is OutgoingContent || ignoredTypes.any { it.isInstance(body) }) {
            LOGGER.trace(
                "Body type ${body::class} is in ignored types. " +
                    "Skipping ContentNegotiation for ${request.url}."
            )
            return null
        }
        val contentType = request.contentType() ?: run {
            LOGGER.trace("Request doesn't have Content-Type header. Skipping ContentNegotiation for ${request.url}.")
            return null
        }

        if (body is Unit) {
            LOGGER.trace("Sending empty body for ${request.url}")
            request.headers.remove(HttpHeaders.ContentType)
            return EmptyContent
        }

        val matchingRegistrations = registrations.filter { it.contentTypeMatcher.contains(contentType) }
            .takeIf { it.isNotEmpty() } ?: run {
            LOGGER.trace(
                "None of the registered converters match request Content-Type=$contentType. " +
                    "Skipping ContentNegotiation for ${request.url}."
            )
            return null
        }
        if (request.bodyType == null) {
            LOGGER.trace("Request has unknown body type. Skipping ContentNegotiation for ${request.url}.")
            return null
        }
        request.headers.remove(HttpHeaders.ContentType)

        // Pick the first one that can convert the subject successfully
        val serializedContent = matchingRegistrations.firstNotNullOfOrNull { registration ->
            val result = registration.converter.serializeNullable(
                contentType,
                contentType.charset() ?: Charsets.UTF_8,
                request.bodyType!!,
                body.takeIf { it != NullBody }
            )
            if (result != null) {
                LOGGER.trace("Converted request body using ${registration.converter} for ${request.url}")
            }
            result
        } ?: throw ContentConverterException(
            "Can't convert $body with contentType $contentType using converters " +
                matchingRegistrations.joinToString { it.converter.toString() }
        )

        return serializedContent
    }

    @OptIn(InternalAPI::class)
    internal suspend fun convertResponse(
        requestUrl: Url,
        info: TypeInfo,
        body: Any,
        responseContentType: ContentType,
        charset: Charset = Charsets.UTF_8
    ): Any? {
        if (body !is ByteReadChannel) {
            LOGGER.trace("Response body is already transformed. Skipping ContentNegotiation for $requestUrl.")
            return null
        }
        if (info.type in ignoredTypes) {
            LOGGER.trace(
                "Response body type ${info.type} is in ignored types. " +
                    "Skipping ContentNegotiation for $requestUrl."
            )
            return null
        }

        val suitableConverters = registrations
            .filter { it.contentTypeMatcher.contains(responseContentType) }
            .map { it.converter }
            .takeIf { it.isNotEmpty() }
            ?: run {
                LOGGER.trace(
                    "None of the registered converters match response with Content-Type=$responseContentType. " +
                        "Skipping ContentNegotiation for $requestUrl."
                )
                return null
            }

        val result = suitableConverters.deserialize(body, info, charset)
        if (result !is ByteReadChannel) {
            LOGGER.trace("Response body was converted to ${result::class} for $requestUrl.")
        }
        return result
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
                val contentType = context.response.contentType() ?: run {
                    LOGGER.trace("Response doesn't have \"Content-Type\" header, skipping ContentNegotiation plugin")
                    return@intercept
                }
                val charset = context.request.headers.suitableCharset()

                val deserializedBody = plugin.convertResponse(context.request.url, info, body, contentType, charset)
                    ?: return@intercept
                val result = HttpResponseContainer(info, deserializedBody)
                proceedWith(result)
            }
        }
    }
}

public class ContentConverterException(message: String) : Exception(message)
