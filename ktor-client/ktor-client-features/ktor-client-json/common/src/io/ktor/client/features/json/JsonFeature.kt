/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.io.*


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
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * The default [allowedContentTypes] is a list which contains [ContentType.Application.Json]
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [allowedContentTypes] list (`application/json` by default).
 */
class JsonFeature(
    val serializer: JsonSerializer,
    @KtorExperimentalAPI val allowedContentTypes: List<ContentType>,
    private val requestContentType: ContentType = allowedContentTypes.requestContentType()
) {
    constructor(serializer: JsonSerializer) : this(
        serializer,
        listOf(ContentType.Application.Json),
        ContentType.Application.Json
    )

    class Config {
        /**
         * Serializer that will be used for serializing requests and deserializing response bodies.
         *
         * Default value for [serializer] is [defaultSerializer].
         */
        var serializer: JsonSerializer? = null

        /**
         * List of content types that are handled by this feature.
         * It also affects `Accept` request header value.
         * Please note that wildcard content types are supported however at least one content type
         * should be non-wildcard since the first non-wildcard content type is used to send request body.
         */
        @KtorExperimentalAPI
        var allowedContentTypes: List<ContentType> = listOf(ContentType.Application.Json)
            set(newList) {
                require(newList.isNotEmpty()) { "At least one content type should be provided to allowedContentTypes" }
                newList.requestContentType()
                field = newList
            }
    }

    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature {
            val config = Config().apply(block)
            val serializer = config.serializer ?: defaultSerializer()
            val allowedContentTypes = config.allowedContentTypes.toList()

            return JsonFeature(serializer, allowedContentTypes, allowedContentTypes.requestContentType())
        }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.allowedContentTypes.forEach { context.accept(it) }

                if (feature.allowedContentTypes.none { context.contentType()?.match(it) == true })
                    return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                val serializedContent = when (payload) {
                    is EmptyContent -> feature.serializer.write(Unit, feature.requestContentType)
                    else -> feature.serializer.write(payload, feature.requestContentType)
                }

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                if (feature.allowedContentTypes.none { context.response.contentType()?.match(it) == true })
                    return@intercept
                try {
                    proceedWith(HttpResponseContainer(info, feature.serializer.read(info, body.readRemaining())))
                } finally {
                    context.close()
                }
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

private fun List<ContentType>.requestContentType(): ContentType = firstOrNull {
    it.contentType != "*" && it.contentSubtype != "*" && it.parameters.all { parameter ->
        parameter.name != "*" && parameter.value != "*"
    }
} ?: error("It should be at least one non-wildcard content-type: $this")
