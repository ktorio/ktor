/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class, InternalAPI::class)
public class ExperimentalJsonConverter(private val format: Json) : ContentConverter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        val serializer = try {
            format.serializersModule.serializerForTypeInfo(typeInfo)
        } catch (_: SerializationException) {
            guessSerializer(value, format.serializersModule)
        }
        val buffer = Buffer().also {
            format.encodeToSink(
                serializer as KSerializer<Any?>,
                value,
                it
            )
        }
        return ChannelWriterContent(
            body = {
                // copy buffer for replayability using copy-on-write segment sharing
                writeBuffer.transferFrom(buffer.copy())
            },
            contentType = contentType,
            contentLength = buffer.remaining
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val serializer = format.serializersModule.serializerForTypeInfo(typeInfo)
        val contentPacket = content.readRemaining()
        return try {
            format.decodeFromSource(serializer, contentPacket)
        } catch (cause: Throwable) {
            throw JsonConvertException("Illegal input: ${cause.message}", cause)
        }
    }
}
