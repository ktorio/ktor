/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.serialization.*

/**
 * Creates a converter for WebSocket serializing with the specified string [format] and
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

    override suspend fun serializeNullable(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
        return serializationBase.serialize(
            SerializationParameters(
                format,
                value,
                typeInfo,
                charset
            )
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        val serializer = serializerFromTypeInfo(typeInfo, format.serializersModule)

        return when (format) {
            is StringFormat -> {
                if (content is Frame.Text) {
                    format.decodeFromString(serializer, content.readText())
                } else {
                    throw WebsocketDeserializeException(
                        "Unsupported format $format for ${content.frameType.name}",
                        frame = content
                    )
                }
            }
            is BinaryFormat -> {
                if (content is Frame.Binary) {
                    format.decodeFromByteArray(serializer, content.readBytes())
                } else {
                    throw WebsocketDeserializeException(
                        "Unsupported format $format for ${content.frameType.name}",
                        frame = content
                    )
                }
            }
            else -> {
                error("Unsupported format $format")
            }
        }
    }

    override fun isApplicable(frame: Frame): Boolean {
        return frame is Frame.Text || frame is Frame.Binary
    }

    private val serializationBase = object : KotlinxSerializationBase<Frame>(format) {
        override suspend fun serializeContent(parameters: SerializationParameters): Frame {
            return serializeContent(
                parameters.serializer,
                parameters.format,
                parameters.value
            )
        }
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any?
    ): Frame {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any?>, value)
                Frame.Text(content)
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any?>, value)
                Frame.Binary(true, content)
            }
            else -> error("Unsupported format $format")
        }
    }
}
