/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Json [ContentConverter] with kotlinx.serialization.
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    register(ContentType.Application.Json, SerializationConverter())
 * }
 *
 * install(ContentNegotiation) {
 *     serialization(json = Json.nonstrict)
 * }
 * ```
 */
@UseExperimental(ImplicitReflectionSerializer::class)
class SerializationConverter(private val json: Json = Json(DefaultJsonConfiguration)) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        val content = json.stringify(value::class.serializer() as KSerializer<Any>, value)
        return TextContent(content, contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val charset = context.call.request.contentCharset() ?: Charsets.UTF_8

        val content = channel.readRemaining().readText(charset)
        val type = request.type

        return json.parse(type.serializer(), content)
    }

}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType = ContentType.Application.Json,
    json: Json = Json(DefaultJsonConfiguration)
) {
    val converter = SerializationConverter(json)
    register(contentType, converter)
}

/**
 * The default json configuration used in [SerializationConverter]. The settings are:
 * - defaults are serialized
 * - mode is not strict so extra json fields are ignored
 * - pretty printing is disabled
 * - array polymorphism is enabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [JsonConfiguration] for more details.
 */
val DefaultJsonConfiguration: JsonConfiguration = JsonConfiguration.Stable.copy(
    encodeDefaults = true,
    strictMode = false,
    unquoted = false,
    prettyPrint = false,
    useArrayPolymorphism = true
)
