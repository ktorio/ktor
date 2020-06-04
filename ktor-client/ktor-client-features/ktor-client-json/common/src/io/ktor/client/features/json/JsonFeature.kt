/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
expect fun defaultSerializer(): JsonSerializer

/**
 * [HttpClient] feature that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [Config.serializer].
 *
 * The default [Config.serializer] is [GsonSerializer].
 *
 * The default [Config.acceptContentTypes] is a list which contains [ContentType.Application.Json]
 *
 * The default [Config.shouldHandle] accepts any `application/<...>+json` pattern.
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [acceptContentTypes] list (`application/json` by default).
 */
class JsonFeature internal constructor(val config: Config) {
    @Deprecated(
        "Install feature properly instead of direct instantiation.",
        level = DeprecationLevel.ERROR
    )
    constructor(serializer: JsonSerializer) : this(
        Config().apply {
            this.serializer = serializer
        }
    )

    /**
     * [JsonFeature] configuration that is used during installation
     */
    class Config {
        /**
         * Serializer that will be used for serializing requests and deserializing response bodies.
         *
         * Default value for [serializer] is [defaultSerializer].
         */
        var serializer: JsonSerializer
            get() = _serializer ?: { val result = defaultSerializer(); _serializer = result; result }()
            set(value) { _serializer = value }
        private var _serializer: JsonSerializer? = null

        /**
         * Backing field with mutable list of content types that are handled by this feature.
         */
        private val _acceptContentTypes: MutableList<ContentType> = mutableListOf(ContentType.Application.Json)

        /**
         * List of content types that are handled by this feature.
         * It also affects `Accept` request header value.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        var acceptContentTypes: List<ContentType>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }
                _acceptContentTypes.clear()
                _acceptContentTypes.addAll(value)
            }
            get() = _acceptContentTypes

        /**
         * Adds accepted content types.
         */
        fun accept(vararg contentTypes: ContentType) {
            _acceptContentTypes += contentTypes
        }

        /**
         * Defines when to handle a given [ContentType] (used in addition to [accept]).
         *
         * By default accepts any `application/<...>+json` pattern.
         */
        var shouldHandle: (ContentType) -> Boolean = {
            val value = it.toString()
            value.startsWith("application/") && value.endsWith("+json")
        }
    }

    /**
     * Companion object for feature installation
     */
    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature =
            JsonFeature(Config().apply(block))

        override fun install(feature: JsonFeature, scope: HttpClient) {
            val config = feature.config
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                val contentType = context.contentType() ?: return@intercept
                if (config.acceptContentTypes.none { contentType.match(it) } && !config.shouldHandle(contentType))
                    return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                val serializedContent = when (payload) {
                    is EmptyContent -> config.serializer.write(Unit, contentType)
                    else -> config.serializer.write(payload, contentType)
                }

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                val contentType = context.response.contentType()
                if (contentType == null ||
                    config.acceptContentTypes.none { contentType.match(it) } &&
                    !config.shouldHandle(contentType)) {
                    return@intercept
                }

                val parsedBody = config.serializer.read(info, body.readRemaining())
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

/**
 * Install [JsonFeature].
 */
fun HttpClientConfig<*>.Json(block: JsonFeature.Config.() -> Unit) {
    install(JsonFeature, block)
}
