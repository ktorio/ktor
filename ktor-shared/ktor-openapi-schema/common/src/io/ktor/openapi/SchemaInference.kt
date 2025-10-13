/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*

public inline fun <reified T : Any> jsonSchema(): Schema {
    return try {
        // Try to get the serializer for type T
        serializer<T>().descriptor.buildJsonSchema()
    } catch (e: SerializationException) {
        // Type T is not serializable
        Schema(
            type = Schema.SchemaType.JsonType.Object,
            description = "Not serializable type: ${T::class.simpleName}"
        )
    }
}

/**
 * Generate a Schema from a SerialDescriptor.
 */
public fun SerialDescriptor.buildJsonSchema(): Schema {
    return when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            val properties = mutableMapOf<String, ReferenceOr<Schema>>()
            val required = mutableListOf<String>()

            for (i in 0 until elementsCount) {
                val name = getElementName(i)
                val elementDescriptor = getElementDescriptor(i)
                val isNullable = elementDescriptor.isNullable

                // Add non-nullable fields to required list
                if (!isNullable) {
                    required.add(name)
                }

                properties[name] = ReferenceOr.Value(elementDescriptor.buildJsonSchema())
            }

            Schema(
                type = Schema.SchemaType.JsonType.Object,
                properties = properties,
                required = required
            )
        }
        StructureKind.LIST -> {
            Schema(
                type = Schema.SchemaType.JsonType.Array,
                items = ReferenceOr.Value(getElementDescriptor(0).buildJsonSchema())
            )
        }
        StructureKind.MAP -> {
            Schema(
                type = Schema.SchemaType.JsonType.Object,
                additionalProperties = AdditionalProperties.PSchema(
                    ReferenceOr.Value(getElementDescriptor(1).buildJsonSchema())
                )
            )
        }
        PrimitiveKind.STRING -> Schema(type = Schema.SchemaType.JsonType.String)
        PrimitiveKind.BOOLEAN -> Schema(type = Schema.SchemaType.JsonType.Boolean)
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
            Schema(type = Schema.SchemaType.JsonType.Integer)
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> Schema(type = Schema.SchemaType.JsonType.Number)
        SerialKind.ENUM -> {
            val enumValues = List(elementsCount) { i -> getElementName(i) }
            Schema(
                type = Schema.SchemaType.JsonType.String,
                enum = enumValues
            )
        }
        SerialKind.CONTEXTUAL -> {
            // For contextual serializers, we need to get the actual serializer from the context
            Schema(type = Schema.SchemaType.JsonType.Object)
        }
        else -> Schema(type = Schema.SchemaType.JsonType.Object) // Default for other kinds
    }
}
