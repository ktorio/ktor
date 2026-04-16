/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for [ContentType] that uses [ContentType.toString] and [ContentType.parse]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.ContentTypeSerializer)
 */
public object ContentTypeSerializer : KSerializer<ContentType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ContentType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ContentType) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): ContentType {
        val raw = decoder.decodeString()
        return try {
            ContentType.parse(raw)
        } catch (cause: Throwable) {
            throw SerializationException("Invalid ContentType: $raw", cause)
        }
    }
}
