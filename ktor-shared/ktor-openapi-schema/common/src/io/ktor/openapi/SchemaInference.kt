/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*

/**
 * Generates a JSON Schema representation for the given type [T].
 *
 * This function attempts to infer the schema from the type's [KSerializer].
 * If the type is not serializable, returns a Schema representing a non-serializable type.
 *
 * @return A [Schema] object representing the JSON Schema for type [T].
 */
public inline fun <reified T : Any> jsonSchema(): Schema {
    return try {
        serializer<T>().descriptor.buildJsonSchema()
    } catch (e: SerializationException) {
        Schema(
            type = Schema.SchemaType.JsonType.`object`,
            description = "Failed to resolve schema for ${T::class.simpleName}. ${e::class.simpleName}: ${e.message}"
        )
    }
}

/**
 * Generates a JSON Schema representation from a Kotlinx Serialization [SerialDescriptor].
 *
 * Supports the following descriptor kinds:
 * - CLASS/OBJECT: Maps to object schema with properties and required fields (based on nullability)
 * - LIST: Maps to array schema with items
 * - MAP: Maps to object schema with additionalProperties
 * - Primitives (STRING, BOOLEAN, INT, LONG, FLOAT, DOUBLE, etc.): Maps to corresponding JSON types
 * - ENUM: Maps to string schema with enum values
 * - CONTEXTUAL: Returns a generic object schema (actual type resolution requires serialization context)
 *
 * @return A [Schema] object representing the JSON Schema for this descriptor.
 *
 * Note: This function does not handle circular references. For types with circular dependencies,
 * consider implementing depth tracking or schema references to avoid stack overflow.
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
                if (!isNullable && !isElementOptional(i)) {
                    required.add(name)
                }

                properties[name] = ReferenceOr.Value(elementDescriptor.buildJsonSchema())
            }

            Schema(
                type = Schema.SchemaType.JsonType.`object`,
                properties = properties,
                required = required
            )
        }
        StructureKind.LIST -> {
            Schema(
                type = Schema.SchemaType.JsonType.array,
                items = ReferenceOr.Value(getElementDescriptor(0).buildJsonSchema())
            )
        }
        StructureKind.MAP -> {
            Schema(
                type = Schema.SchemaType.JsonType.`object`,
                additionalProperties = AdditionalProperties.PSchema(
                    ReferenceOr.Value(getElementDescriptor(1).buildJsonSchema())
                )
            )
        }
        PrimitiveKind.STRING -> Schema(type = Schema.SchemaType.JsonType.string)
        PrimitiveKind.BOOLEAN -> Schema(type = Schema.SchemaType.JsonType.boolean)
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
            Schema(type = Schema.SchemaType.JsonType.integer)
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> Schema(type = Schema.SchemaType.JsonType.number)
        SerialKind.ENUM -> {
            val enumValues = List(elementsCount) { i -> GenericElement(getElementName(i)) }
            Schema(
                type = Schema.SchemaType.JsonType.string,
                enum = enumValues
            )
        }
        SerialKind.CONTEXTUAL -> {
            // For contextual serializers, we need to get the actual serializer from the context
            Schema(type = Schema.SchemaType.JsonType.`object`)
        }
        else -> Schema(type = Schema.SchemaType.JsonType.`object`) // Default for other kinds
    }
}
