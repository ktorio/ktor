/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * Used for referencing schema from OpenApi specification.  The construction of the actual schema can
 * be deferred when using type references.  This avoids unnecessary overhead and allows flexibility for
 * schema generation.
 */
@Serializable(JsonSchemaReferenceSerializer::class)
public class JsonSchemaReference private constructor(
    public val type: KType?,
    private var _schema: JsonSchema?,
) {
    public companion object {
        public fun ofType(type: KType): JsonSchemaReference = JsonSchemaReference(type, null)
        public fun of(schema: JsonSchema): JsonSchemaReference = JsonSchemaReference(null, schema)
    }

    public fun get(inferenceFunction: (KType) -> JsonSchema = ::buildKotlinxSerializationSchema): JsonSchema =
        _schema ?: inferenceFunction(type!!).also {
            _schema = it
        }
}

public class JsonSchemaReferenceSerializer : KSerializer<JsonSchemaReference> {
    private val schemaSerializer = serializer<JsonSchema>()
    override val descriptor: SerialDescriptor = schemaSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: JsonSchemaReference
    ) {
        schemaSerializer.serialize(encoder, value.get(::buildKotlinxSerializationSchema))
    }

    override fun deserialize(decoder: Decoder): JsonSchemaReference {
        return JsonSchemaReference.of(schemaSerializer.deserialize(decoder))
    }
}
