/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.common.serialization.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * [HttpClient] feature that serializes/de-serializes custom objects
 * to request and from response bodies using a [ContentConverter]
 * to Content-Type and Accept headers.
 */
public class ContentNegotiation internal constructor(
    internal val registrations: List<Config.ConverterRegistration>
) {

    /**
     * [ContentNegotiation] configuration that is used during installation
     */
    public class Config : Configuration {

        internal class ConverterRegistration(
            val converter: ContentConverter,
            val contentTypeToSend: ContentType,
            val contentTypeMatcher: ContentTypeMatcher,
        )

        internal val registrations = mutableListOf<ConverterRegistration>()

        /**
         * Registers a [contentType] to a specified [converter] with an optional [configuration] script for converter
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
         * an optional [configuration] script for converter
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
     * Companion object for feature installation
     */
    public companion object Feature : HttpClientFeature<Config, ContentNegotiation> {
        public override val key: AttributeKey<ContentNegotiation> = AttributeKey("ContentNegotiation")

        override fun prepare(block: Config.() -> Unit): ContentNegotiation {
            val config = Config().apply(block)
            return ContentNegotiation(config.registrations)
        }

        override fun install(feature: ContentNegotiation, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                val registrations = feature.registrations
                registrations.forEach { context.accept(it.contentTypeToSend) }

                val contentType = context.contentType() ?: return@intercept
                context.headers.remove(HttpHeaders.ContentType)

                if (payload is Unit || payload is EmptyContent) {
                    proceedWith(EmptyContent)
                    return@intercept
                }
                val registration = registrations
                    .firstOrNull { it.contentTypeMatcher.contains(contentType) } ?: return@intercept

                val serializedContent = registration.converter.serialize(
                    contentType,
                    contentType.charset() ?: Charsets.UTF_8,
                    context.bodyType!!,
                    payload
                ) ?: throw ContentConverterException(
                    "Can't convert $payload with contentType $contentType using converter ${registration.converter}"
                )

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                val contentType = context.response.contentType() ?: return@intercept
                val registrations = feature.registrations
                val registration = registrations
                    .firstOrNull { it.contentTypeMatcher.contains(contentType) } ?: return@intercept

                val parsedBody = registration.converter
                    .deserialize(context.request.headers.suitableCharset(), info, body) ?: return@intercept
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

public class ContentConverterException(message: String) : Exception(message)
