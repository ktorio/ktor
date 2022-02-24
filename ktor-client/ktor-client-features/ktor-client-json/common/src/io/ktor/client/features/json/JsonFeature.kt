/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
public expect fun defaultSerializer(): JsonSerializer

/**
 * [HttpClient] feature that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * The default [acceptContentTypes] is a list which contains [ContentType.Application.Json]
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [acceptContentTypes] list (`application/json` by default).
 *
 * @property serializer that is used to serialize and deserialize request/response bodies
 * @property acceptContentTypes that are allowed when receiving content
 */
public class JsonFeature internal constructor(
    public val serializer: JsonSerializer,
    public val acceptContentTypes: List<ContentType> = listOf(ContentType.Application.Json),
    private val receiveContentTypeMatchers: List<ContentTypeMatcher> = listOf(JsonContentTypeMatcher()),
) {
    @Deprecated("Install feature properly instead of direct instantiation.", level = DeprecationLevel.ERROR)
    public constructor(serializer: JsonSerializer) : this(serializer, listOf(ContentType.Application.Json))

    internal constructor(config: Config) : this(
        config.serializer ?: defaultSerializer(),
        config.acceptContentTypes,
        config.receiveContentTypeMatchers
    )

    /**
     * [JsonFeature] configuration that is used during installation
     */
    public class Config {
        /**
         * Serializer that will be used for serializing requests and deserializing response bodies.
         *
         * Default value for [serializer] is [defaultSerializer].
         */
        public var serializer: JsonSerializer? = null

        /**
         * Backing field with mutable list of content types that are handled by this feature.
         */
        private val _acceptContentTypes: MutableList<ContentType> = mutableListOf(ContentType.Application.Json)
        private val _receiveContentTypeMatchers: MutableList<ContentTypeMatcher> =
            mutableListOf(JsonContentTypeMatcher())

        /**
         * List of content types that are handled by this feature.
         * It also affects `Accept` request header value.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        public var acceptContentTypes: List<ContentType>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }

                _acceptContentTypes.clear()
                _acceptContentTypes.addAll(value)
            }
            get() = _acceptContentTypes

        /**
         * List of content type matchers that are handled by this feature.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        public var receiveContentTypeMatchers: List<ContentTypeMatcher>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }
                _receiveContentTypeMatchers.clear()
                _receiveContentTypeMatchers.addAll(value)
            }
            get() = _receiveContentTypeMatchers

        /**
         * Adds accepted content types. Be aware that [ContentType.Application.Json] accepted by default is removed from
         * the list if you use this function to provide accepted content types.
         * It also affects `Accept` request header value.
         */
        public fun accept(vararg contentTypes: ContentType) {
            _acceptContentTypes += contentTypes
        }

        /**
         * Adds accepted content types. Existing content types will not be removed.
         */
        public fun receive(matcher: ContentTypeMatcher) {
            _receiveContentTypeMatchers += matcher
        }
    }

    internal fun canHandle(contentType: ContentType): Boolean {
        val accepted = acceptContentTypes.any { contentType.match(it) }
        val matchers = receiveContentTypeMatchers

        return accepted || matchers.any { matcher -> matcher.contains(contentType) }
    }

    /**
     * Companion object for feature installation
     */
    public companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature {
            val config = Config().apply(block)
            val serializer = config.serializer ?: defaultSerializer()
            val allowedContentTypes = config.acceptContentTypes.toList()
            val receiveContentTypeMatchers = config.receiveContentTypeMatchers

            return JsonFeature(serializer, allowedContentTypes, receiveContentTypeMatchers)
        }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.acceptContentTypes.forEach { context.accept(it) }

                val contentType = context.contentType() ?: return@intercept
                if (!feature.canHandle(contentType)) return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                val serializedContent = when (payload) {
                    Unit -> EmptyContent
                    is EmptyContent -> EmptyContent
                    else -> feature.serializer.write(payload, contentType)
                }

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                val contentType = context.response.contentType() ?: return@intercept
                if (!feature.canHandle(contentType)) return@intercept

                val parsedBody = feature.serializer.read(info, body.readRemaining())
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

/**
 * Install [JsonFeature].
 */
public fun HttpClientConfig<*>.Json(block: JsonFeature.Config.() -> Unit) {
    install(JsonFeature, block)
}
