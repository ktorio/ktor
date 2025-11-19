/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Context interface for creating schema from type metadata.
 */
public fun interface JsonSchemaInference {
    /**
     * Builds a [JsonSchema] for the given [type].
     */
    public fun buildSchema(type: KType): JsonSchema
}

public val KotlinxJsonSchemaInference: JsonSchemaInference = JsonSchemaInference { type ->
    serializer(type).descriptor.buildJsonSchema()
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
 * @param schema the base instance for the resulting schema, use this to include extra properties
 *
 * @return A [JsonSchema] object representing the JSON Schema for this descriptor.
 *
 * Note: This function does not handle circular references. For types with circular dependencies,
 * consider implementing depth tracking or schema references to avoid stack overflow.
 */
public fun SerialDescriptor.buildJsonSchema(): JsonSchema {
    return when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
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

            JsonSchema(
                title = serialName,
                type = JsonSchema.JsonType.OBJECT,
                properties = properties,
                required = required
            )
        }
        StructureKind.LIST -> {
            JsonSchema(
                type = JsonSchema.JsonType.ARRAY,
                items = ReferenceOr.Value(getElementDescriptor(0).buildJsonSchema())
            )
        }
        StructureKind.MAP -> {
            JsonSchema(
                type = JsonSchema.JsonType.OBJECT,
                additionalProperties = AdditionalProperties.PSchema(
                    ReferenceOr.Value(getElementDescriptor(1).buildJsonSchema())
                )
            )
        }
        PrimitiveKind.STRING ->
            JsonSchema(type = JsonSchema.JsonType.STRING)
        PrimitiveKind.BOOLEAN ->
            JsonSchema(type = JsonSchema.JsonType.BOOLEAN)
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
            JsonSchema(type = JsonSchema.JsonType.INTEGER)
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
            JsonSchema(type = JsonSchema.JsonType.NUMBER)
        SerialKind.ENUM -> {
            val enumValues = List(elementsCount) { i -> GenericElement(getElementName(i)) }
            JsonSchema(
                type = JsonSchema.JsonType.STRING,
                enum = enumValues
            )
        }
        SerialKind.CONTEXTUAL -> {
            // For contextual serializers, we need to get the actual serializer from the context
            JsonSchema(type = JsonSchema.JsonType.OBJECT)
        }
        else -> JsonSchema(type = JsonSchema.JsonType.OBJECT) // Default for other kinds
    }
}

/**
 * Generates a JSON Schema representation for the given type [T].
 *
 * This function attempts to infer the schema from the type's [KSerializer].
 * If the type is not serializable, returns a Schema representing a non-serializable type.
 *
 * @return A [JsonSchema] object representing the JSON Schema for type [T].
 */
public inline fun <reified T : Any> JsonSchemaInference.jsonSchema(): JsonSchema {
    return try {
        buildSchema(typeOf<T>())
    } catch (e: SerializationException) {
        JsonSchema(
            type = JsonSchema.JsonType.OBJECT,
            description = "Failed to resolve schema for ${T::class.simpleName}. ${e::class.simpleName}: ${e.message}"
        )
    }
}

/**
 * Generates JSON schema from the type's [KSerializer] descriptor.
 *
 * This is used as the default for inferring schema.
 */
public fun buildKotlinxSerializationSchema(type: KType): JsonSchema {
    return serializer(type)
        .descriptor
        .buildJsonSchema()
}
