/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal abstract class SerializerWithExtensions<T>(
    private val serializer: KSerializer<T>,
    private val withExtensions: (T, Map<String, GenericElement>) -> T,
) : KSerializer<T> {
    private val adapters: List<GenericElementSerialAdapter> = listOf(JsonElementSerialAdapter)

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        val element = adapters.firstNotNullOfOrNull {
            it.trySerializeToElement(encoder, value, serializer)
        } ?: error { "No adapter found for $encoder" }
        val (base, extensions) = element.split { it == "extensions" }
        when (val extensionsValue = extensions.entries().firstOrNull()?.second) {
            null -> encoder.encodeSerializableValue(serializer, value)
            else -> encoder.encodeSerializableValue(
                GenericElement.serializer(),
                base + extensionsValue
            )
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val merged = decoder.decodeSerializableValue(GenericElement.serializer())
        val (base, extensions) = merged.split { key ->
            key.startsWith("x-")
        }
        return withExtensions(
            base.deserialize(serializer),
            extensions.entries().toMap()
        )
    }
}
