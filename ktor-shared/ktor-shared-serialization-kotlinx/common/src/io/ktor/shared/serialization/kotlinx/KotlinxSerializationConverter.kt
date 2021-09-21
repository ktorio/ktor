/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.shared.serialization.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlin.jvm.*

/**
 * Creates a converter serializing with the specified string [format] and
 * [defaultCharset] (optional, usually it is UTF-8).
 */
@OptIn(ExperimentalSerializationApi::class)
public class KotlinxSerializationConverter(
    private val format: SerialFormat,
    extraSerializerMatchers: Map<KSerializer<*>, (value: Any) -> Boolean> = mutableMapOf()
) : ContentConverter {
    private val extraSerializerMatchers = extraSerializerMatchers.toMutableMap()

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    /**
     * Registers extra [matcher] for a given [serializer] to be used when guessing serializers
     */
    public fun match(serializer: KSerializer<*>, matcher: (value: Any) -> Boolean) {
        extraSerializerMatchers[serializer] = matcher
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent {
        val result = try {
            serializerFromTypeInfo(typeInfo, format.serializersModule).let {
                serializeContent(it, format, value, contentType, charset)
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

        val matchedSerializer = extraSerializerMatchers.entries.firstOrNull { (_, matcher) ->
            matcher(value)
        }?.key
        val guessedSearchSerializer = matchedSerializer ?: guessSerializer(value, format.serializersModule)
        return serializeContent(guessedSearchSerializer, format, value, contentType, charset)
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any,
        contentType: ContentType,
        charset: Charset
    ): OutgoingContent.ByteArrayContent {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any>, value)
                TextContent(content, contentType.withCharset(charset))
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any>, value)
                ByteArrayContent(content, contentType)
            }
            else -> error("Unsupported format $format")
        }
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val serializer = serializerFromTypeInfo(typeInfo, format.serializersModule)
        val contentPacket = content.readRemaining()

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

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] plugin
 * with the specified [contentType] and binary [format] (such as CBOR, ProtoBuf)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.serialization(
    contentType: ContentType,
    format: BinaryFormat
) {
    register(
        contentType,
        KotlinxSerializationConverter(format)
    )
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] plugin
 * with the specified [contentType] and string [format] (such as Json)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.serialization(
    contentType: ContentType,
    format: StringFormat
) {
    register(
        contentType,
        KotlinxSerializationConverter(format)
    )
}
