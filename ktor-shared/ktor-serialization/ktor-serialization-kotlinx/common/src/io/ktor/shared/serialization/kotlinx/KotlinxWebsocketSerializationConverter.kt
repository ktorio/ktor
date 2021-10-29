/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*

/**
 * Creates a converter for websocket serializing with the specified string [format] and
 * [defaultCharset] (optional, usually it is UTF-8).
 */
@OptIn(ExperimentalSerializationApi::class)
public class KotlinxWebsocketSerializationConverter(
    private val format: SerialFormat,
) : WebsocketContentConverter {

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): Frame {
        val result = try {
            serializerFromTypeInfo(typeInfo, format.serializersModule).let {
                serializeContent(it, format, value)
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
        return serializeContent(guessedSearchSerializer, format, value)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
        val serializer = serializerFromTypeInfo(typeInfo, format.serializersModule)

        return when (format) {
            is StringFormat -> {
                if (content is Frame.Text)
                    format.decodeFromString(serializer, content.readText())
                else
                    throw WebsocketConverterNotFoundException(
                        "Unsupported format $format for ${content.frameType.name}"
                    )
            }
            is BinaryFormat -> {
                if (content is Frame.Binary)
                    format.decodeFromByteArray(serializer, content.readBytes())
                else
                    throw WebsocketConverterNotFoundException(
                        "Unsupported format $format for ${content.frameType.name}"
                    )
            }
            else -> {
                error("Unsupported format $format")
            }
        } ?: throw WebsocketConverterNotFoundException("Unsupported format $format for ${content.frameType.name}")
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any
    ): Frame {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any>, value)
                Frame.Text(content)
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any>, value)
                Frame.Binary(true, content)
            }
            else -> error("Unsupported format $format")
        }
    }
}
