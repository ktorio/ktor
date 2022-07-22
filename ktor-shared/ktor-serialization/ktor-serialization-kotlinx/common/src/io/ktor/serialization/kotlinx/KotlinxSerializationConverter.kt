/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
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
) : ContentConverter {
    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        return serializationBase.serialize(
            SerializationNegotiationParameters(
                format,
                value,
                typeInfo,
                charset,
                contentType
            )
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val serializer = serializerFromTypeInfo(typeInfo, format.serializersModule)
        val contentPacket = content.readRemaining()

        try {
            return when (format) {
                is StringFormat -> format.decodeFromString(serializer, contentPacket.readText(charset))
                is BinaryFormat -> format.decodeFromByteArray(serializer, contentPacket.readBytes())
                else -> {
                    contentPacket.discard()
                    error("Unsupported format $format")
                }
            }
        } catch (cause: Throwable) {
            throw JsonConvertException("Illegal input", cause)
        }
    }

    private val serializationBase = object : KotlinxSerializationBase<OutgoingContent.ByteArrayContent>(format) {
        override suspend fun serializeContent(parameters: SerializationParameters): OutgoingContent.ByteArrayContent {
            if (parameters !is SerializationNegotiationParameters) {
                error(
                    "parameters type is ${parameters::class.simpleName}," +
                        " but expected ${SerializationNegotiationParameters::class.simpleName}"
                )
            }
            return serializeContent(
                parameters.serializer,
                parameters.format,
                parameters.value,
                parameters.contentType,
                parameters.charset
            )
        }
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any?,
        contentType: ContentType,
        charset: Charset
    ): OutgoingContent.ByteArrayContent {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any?>, value)
                TextContent(content, contentType.withCharsetIfNeeded(charset))
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any?>, value)
                ByteArrayContent(content, contentType)
            }
            else -> error("Unsupported format $format")
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
