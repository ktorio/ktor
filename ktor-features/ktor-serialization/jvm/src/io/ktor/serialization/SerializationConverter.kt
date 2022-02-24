/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public fun SerializationConverter(): SerializationConverter =
    SerializationConverter(DefaultJson)

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
@OptIn(ExperimentalSerializationApi::class)
public class SerializationConverter
private constructor(
    private val format: SerialFormat,
    private val defaultCharset: Charset = Charsets.UTF_8
) : ContentConverter {

    /**
     * Creates a converter serializing with the specified binary [format].
     */
    public constructor(format: BinaryFormat) : this(format as SerialFormat)

    /**
     * Creates a converter serializing with the specified string [format] and
     * [defaultCharset] (optional, usually it is UTF-8).
     */
    public constructor(
        format: StringFormat,
        defaultCharset: Charset = Charsets.UTF_8
    ) : this(format as SerialFormat, defaultCharset)

    @Suppress("unused")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public constructor(json: Json = DefaultJson) : this(json as StringFormat)

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
    public constructor() : this(DefaultJson)

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
        val result = try {
            serializerFromResponseType(context, format.serializersModule)?.let {
                serializeContent(it, format, value, contentType, context)
            }
        } catch (cause: SerializationException) {
            // can fail due to
            // 1. https://github.com/Kotlin/kotlinx.serialization/issues/1163)
            // 2. mismatching between compile-time and runtime types of the response.
            null
        }
        if (result != null) {
            return result
        }

        val guessedSearchSerializer = guessSerializer(value, format.serializersModule)
        return serializeContent(guessedSearchSerializer, format, value, contentType, context)
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any,
        contentType: ContentType,
        context: PipelineContext<Any, ApplicationCall>
    ): OutgoingContent.ByteArrayContent {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any>, value)
                TextContent(content, contentType.withCharset(context.call.suitableCharset()))
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any>, value)
                ByteArrayContent(content, contentType)
            }
            else -> error("Unsupported format $format")
        }
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val charset = context.call.request.contentCharset() ?: defaultCharset

        val serializer = format.serializersModule.serializer(request.typeInfo)
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

@Suppress("unused")
@Deprecated("Use json function instead.", level = DeprecationLevel.HIDDEN)
@JvmName("serialization")
public fun ContentNegotiation.Configuration.serialization0(
    contentType: ContentType = ContentType.Application.Json,
    json: Json = DefaultJson
) {
    json(json, contentType)
}
