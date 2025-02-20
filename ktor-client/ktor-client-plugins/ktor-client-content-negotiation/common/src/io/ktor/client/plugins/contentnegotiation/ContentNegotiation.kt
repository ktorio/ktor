/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.AttributeKey
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
 * The content types that are excluded from the `Accept` header for this specific request. Use the
 * [exclude] `HttpRequestBuilder` extension to set this attribute on a request.
 */
internal val ExcludedContentTypes: AttributeKey<List<ContentType>> = AttributeKey("ExcludedContentTypesAttr")

/**
 * A [ContentNegotiation] configuration that is used during installation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig)
 */
@KtorDsl
public class ContentNegotiationConfig : Configuration {

    internal class ConverterRegistration(
        val converter: ContentConverter,
        val contentTypeToSend: ContentType,
        val contentTypeMatcher: ContentTypeMatcher
    )

    internal val ignoredTypes: MutableSet<KClass<*>> =
        (DefaultIgnoredTypes + DefaultCommonIgnoredTypes).toMutableSet()

    internal val registrations = mutableListOf<ConverterRegistration>()

    /**
     * By default, `Accept` headers for registered content types will have no q value (implicit 1.0). Set this to
     * change that behavior. This is useful to override the preferred `Accept` content types on a per-request basis.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.defaultAcceptHeaderQValue)
     */
    public var defaultAcceptHeaderQValue: Double? = null

    /**
     * Registers a [contentType] to a specified [converter] with an optional [configuration] script for a converter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.register)
     */
    public override fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit
    ) {
        val matcher = when {
            contentType.match(ContentType.Application.Json) -> JsonContentTypeMatcher
            else -> defaultMatcher(contentType)
        }
        register(contentType, converter, matcher, configuration)
    }

    /**
     * Registers a [contentTypeToSend] and [contentTypeMatcher] to a specified [converter] with
     * an optional [configuration] script for a converter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.register)
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.ignoreType)
     */
    public inline fun <reified T> ignoreType() {
        ignoreType(T::class)
    }

    /**
     * Remove [T] from the list of types that should be ignored by [ContentNegotiation].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.removeIgnoredType)
     */
    public inline fun <reified T> removeIgnoredType() {
        removeIgnoredType(T::class)
    }

    /**
     * Remove [type] from the list of types that should be ignored by [ContentNegotiation].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.removeIgnoredType)
     */
    public fun removeIgnoredType(type: KClass<*>) {
        ignoredTypes.remove(type)
    }

    /**
     * Adds a [type] to the list of types that should be ignored by [ContentNegotiation].
     *
     * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.ignoreType)
     */
    public fun ignoreType(type: KClass<*>) {
        ignoredTypes.add(type)
    }

    /**
     * Clear all configured ignored types including defaults.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig.clearIgnoredTypes)
     */
    public fun clearIgnoredTypes() {
        ignoredTypes.clear()
    }

    private fun defaultMatcher(pattern: ContentType): ContentTypeMatcher = object : ContentTypeMatcher {
        override fun contains(contentType: ContentType): Boolean = contentType.match(pattern)
    }
}

/**
 * A plugin that serves two primary purposes:
 * - Negotiating media types between the client and server. For this, it uses the `Accept` and `Content-Type` headers.
 * - Serializing/deserializing the content in a specific format when sending requests and receiving responses.
 *    Ktor supports the following formats out-of-the-box: `JSON`, `XML`, and `CBOR`.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization-client.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.ContentNegotiation)
 */
@OptIn(InternalAPI::class)
public val ContentNegotiation: ClientPlugin<ContentNegotiationConfig> = createClientPlugin(
    "ContentNegotiation",
    ::ContentNegotiationConfig
) {
    val registrations: List<ContentNegotiationConfig.ConverterRegistration> = pluginConfig.registrations
    val ignoredTypes: Set<KClass<*>> = pluginConfig.ignoredTypes

    suspend fun convertRequest(request: HttpRequestBuilder, body: Any): OutgoingContent? {
        val requestRegistrations = if (request.attributes.contains(ExcludedContentTypes)) {
            val excluded = request.attributes[ExcludedContentTypes]
            registrations.filter { registration -> excluded.none { registration.contentTypeToSend.match(it) } }
        } else {
            registrations
        }

        val acceptHeaders = request.headers.getAll(HttpHeaders.Accept).orEmpty()
        requestRegistrations.forEach {
            if (acceptHeaders.none { h -> ContentType.parse(h).match(it.contentTypeToSend) }) {
                // automatically added headers get a lower content type priority, so user-specified accept headers
                //  with higher q or implicit q=1 will take precedence
                val contentTypeToSend = when (val qValue = pluginConfig.defaultAcceptHeaderQValue) {
                    null -> it.contentTypeToSend
                    else -> it.contentTypeToSend.withParameter("q", qValue.toString())
                }
                LOGGER.trace("Adding Accept=$contentTypeToSend header for ${request.url}")
                request.accept(contentTypeToSend)
            }
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
            val result = registration.converter.serialize(
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
    suspend fun convertResponse(
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

    transformRequestBody { request, body, _ ->
        convertRequest(request, body)
    }

    transformResponseBody { response, body, info ->
        val contentType = response.contentType() ?: return@transformResponseBody null
        val charset = response.request.headers.suitableCharset()

        convertResponse(response.request.url, info, body, contentType, charset)
    }
}

public class ContentConverterException(message: String) : Exception(message)

/**
 * Excludes the given [ContentType] from the list of types that will be sent in the `Accept` header by
 * the [ContentNegotiation] plugin. Can be used to not accept specific types for particular requests.
 * This can be called multiple times to exclude multiple content types, or multiple content types can
 * be passed in a single call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.contentnegotiation.exclude)
 */
public fun HttpRequestBuilder.exclude(vararg contentType: ContentType) {
    val excludedContentTypes = attributes.getOrNull(ExcludedContentTypes).orEmpty()
    attributes.put(ExcludedContentTypes, excludedContentTypes + contentType)
}
