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
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.text.Charsets

/**
 * This is no longer supported. Instead, specify format explicitly or
 * use the corresponding DSL function.
 *
 * ```kotlin
 * install(ContentNegotiation) {
 *     json() // json with the default config
 *     json(Json.strict) // strict json
 *     register(..., SerializationConverter(Json(Json.nonstrict)) // more generic and longer way
 * }
 * ```
 */
@Suppress("FunctionName", "UNUSED", "CONFLICTING_OVERLOADS")
@Deprecated(
    "Specify format explicitly. E.g SerializationConverter(Json(...))",
    replaceWith = ReplaceWith(
        "SerializationConverter(Json(DefaultJsonConfiguration))",
        "io.ktor.serialization.DefaultJsonConfiguration",
        "kotlinx.serialization.json.Json"
    )
)
fun SerializationConverter(): SerializationConverter =
    SerializationConverter(DefaultJsonConfiguration)

/**
 * [ContentConverter] with kotlinx.serialization.
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    json()
 *    json(ContentType.Application.Json, Json.nonstrict)
 *    cbor()
 *    protoBuf()
 * }
 * ```
 */
@Suppress("EXPERIMENTAL_API_USAGE_ERROR")
class SerializationConverter private constructor(
    private val format: SerialFormat,
    private val defaultCharset: Charset = Charsets.UTF_8
) : ContentConverter {

    /**
     * Creates a converter serializing with the specified binary [format].
     */
    constructor(format: BinaryFormat) : this(format as SerialFormat)

    /**
     * Creates a converter serializing with the specified string [format] and
     * [defaultCharset] (optional, usually it is UTF-8).
     */
    constructor(
        format: StringFormat,
        defaultCharset: Charset = Charsets.UTF_8
    ) : this(format as SerialFormat, defaultCharset)

    @Suppress("unused")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    constructor(json: Json = DefaultJsonConfiguration) : this(json as StringFormat)

    /**
     * This is no longer supported. Instead, specify format explicitly or
     * use the corresponding DSL function.
     *
     * ```kotlin
     * install(ContentNegotiation) {
     *     json() // json with the default config
     *     json(Json.strict) // strict json
     *     register(..., SerializationConverter(Json(Json.nonstrict)) // more generic and longer way
     * }
     * ```
     */
    @Suppress("UNUSED", "CONFLICTING_OVERLOADS")
    @Deprecated(
        "Specify format explicitly. E.g SerializationConverter(Json(...))",
        level = DeprecationLevel.HIDDEN
    )
    constructor() : this(DefaultJsonConfiguration)

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        val serializer = serializerForSending(value, format.serializersModule) as KSerializer<Any>

        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer, value)
                TextContent(content, contentType.withCharset(context.call.suitableCharset()))
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer, value)
                ByteArrayContent(content, contentType)
            }
            else -> error("Unsupported format $format")
        }
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val charset = context.call.request.contentCharset() ?: defaultCharset

        val serializer = format.serializersModule.getContextual(request.type) ?: serializerByTypeInfo(request.typeInfo)
        val contentPacket = channel.readRemaining()

        return when (format) {
            is StringFormat -> format.decodeFromString(serializer, contentPacket.readText(charset))
            is BinaryFormat -> format.decodeFromByteArray(serializer, contentPacket.readBytes())
            else -> {
                contentPacket.discard()
                error("Unsupported format $format")
            }
        }
    }
}

@Suppress("unused", "CONFLICTING_OVERLOADS")
@Deprecated("Use json function instead.", level = DeprecationLevel.HIDDEN)
fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType = ContentType.Application.Json,
    json: Json = DefaultJsonConfiguration
) {
    json(json, contentType)
}
