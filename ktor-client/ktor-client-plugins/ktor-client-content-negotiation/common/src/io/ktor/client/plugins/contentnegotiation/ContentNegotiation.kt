/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
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

@OptIn(InternalAPI::class)
internal val ContentNegotiationPlugin: ClientPlugin<ContentNegotiation.Config> =
    createClientPlugin("ContentNegotiation", ContentNegotiation::Config) {
        val registrations: List<ContentNegotiation.Config.ConverterRegistration> = pluginConfig.registrations
        val ignoredTypes: Set<KClass<*>> = pluginConfig.ignoredTypes

        transformRequestBody { request, body, bodyType ->
            registrations.forEach { request.accept(it.contentTypeToSend) }

            if (body is OutgoingContent || ignoredTypes.any { it.isInstance(body) }) return@transformRequestBody null
            val contentType = request.contentType() ?: return@transformRequestBody null

            if (body is Unit) {
                request.headers.remove(HttpHeaders.ContentType)
                return@transformRequestBody EmptyContent
            }

            val matchingRegistrations = registrations.filter { it.contentTypeMatcher.contains(contentType) }
                .takeIf { it.isNotEmpty() } ?: return@transformRequestBody null
            if (bodyType == null) return@transformRequestBody null
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

            return@transformRequestBody serializedContent
        }

        transformResponseBody { response, body, info ->
            val contentType = response.contentType() ?: return@transformResponseBody null
            val charset = response.request.headers.suitableCharset()

            if (info.type in ignoredTypes) return@transformResponseBody null

            val suitableConverters = registrations
                .filter { it.contentTypeMatcher.contains(contentType) }
                .map { it.converter }
                .takeIf { it.isNotEmpty() } ?: return@transformResponseBody null

            return@transformResponseBody suitableConverters.deserialize(body, info, charset)
        }
    }

/**
 * A plugin that serves two primary purposes:
 * - Negotiating media types between the client and server. For this, it uses the `Accept` and `Content-Type` headers.
 * - Serializing/deserializing the content in a specific format when sending requests and receiving responses.
 *    Ktor supports the following formats out-of-the-box: `JSON`, `XML`, and `CBOR`.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization-client.html).
 */
public class ContentNegotiation internal constructor() {

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

    /**
     * A companion object used to install a plugin.
     */
    @KtorDsl
    public companion object Plugin : HttpClientPlugin<Config, ClientPluginInstance<Config>> {
        public override val key: AttributeKey<ClientPluginInstance<Config>> = AttributeKey("ContentNegotiation")

        override fun prepare(block: Config.() -> Unit): ClientPluginInstance<Config> {
            return ContentNegotiationPlugin.prepare(block)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ClientPluginInstance<Config>, scope: HttpClient) {
            plugin.install(scope)
        }
    }
}

public class ContentConverterException(message: String) : Exception(message)
