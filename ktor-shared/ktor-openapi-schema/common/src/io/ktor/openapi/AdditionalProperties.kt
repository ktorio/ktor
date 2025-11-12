/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.jvm.JvmInline

/**
 * Represents the OpenAPI Schema Object's additionalProperties field.
 * It can be either a boolean that allows or forbids additional properties,
 * or a schema reference describing the type of those additional properties.
 */
@Serializable(with = AdditionalProperties.Companion.Serializer::class)
public sealed interface AdditionalProperties {
    /** Wrapper for a boolean flag that allows or denies extra properties on an object schema. */
    @JvmInline
    public value class Allowed(public val value: Boolean) : AdditionalProperties

    /** Wrapper for a schema reference describing the type of additional properties. */
    @JvmInline
    public value class PSchema(public val value: ReferenceOr<Schema>) : AdditionalProperties

    public companion object {
        internal object Serializer : KSerializer<AdditionalProperties> {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            override val descriptor =
                buildSerialDescriptor("io.ktor.openapi.AdditionalProperties", SerialKind.CONTEXTUAL)

            override fun deserialize(decoder: Decoder): AdditionalProperties {
                val element: GenericElement = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
                return when {
                    element.isObject() -> {
                        val schemaSerializer: KSerializer<ReferenceOr<Schema>> = decoder.serializersModule.serializer()
                        PSchema(element.deserialize(schemaSerializer))
                    }
                    else -> Allowed(element.deserialize(Boolean.serializer()))
                }
            }

            override fun serialize(encoder: Encoder, value: AdditionalProperties) {
                when (value) {
                    is Allowed -> encoder.encodeBoolean(value.value)
                    is PSchema ->
                        encoder.encodeSerializableValue(
                            ReferenceOr.serializer(Schema.serializer()),
                            value.value,
                        )
                }
            }
        }
    }
}
