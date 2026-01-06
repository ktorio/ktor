/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.AdditionalProperties.*
import io.ktor.openapi.ReferenceOr.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
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

/**
 * Infers JSON schema from kotlinx-serialization descriptors.
 */
public val KotlinxJsonSchemaInference: JsonSchemaInference = JsonSchemaInference { type ->
    serializer(type)
        .descriptor
        .buildJsonSchema(
            // parameterized types cannot be referenced from their serial name
            includeTitle = type.arguments.isEmpty()
        )
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
 * @return A [JsonSchema] object representing the JSON Schema for this descriptor.
 *
 * Note: This function does not handle circular references. For types with circular dependencies,
 * consider implementing depth tracking or schema references to avoid stack overflow.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
public fun SerialDescriptor.buildJsonSchema(
    includeTitle: Boolean = true,
    includeAnnotations: List<Annotation> = emptyList(),
): JsonSchema {
    val reflectJsonSchema: KClass<*>.() -> ReferenceOr<JsonSchema> = {
        Value(this.serializer().descriptor.buildJsonSchema(includeTitle))
    }
    val annotations = includeAnnotations + annotations

    return when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
            val required = mutableListOf<String>()

            for (i in 0 until elementsCount) {
                val name = getElementName(i)
                val elementDescriptor = getElementDescriptor(i)
                val annotations = getElementAnnotations(i)
                if (annotations.any { it is JsonSchemaIgnore }) continue

                if (!isElementOptional(i)) {
                    required.add(name)
                }

                properties[name] = Value(
                    elementDescriptor.buildJsonSchema(
                        includeAnnotations = getElementAnnotations(i)
                    )
                )
            }

            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT,
                title = serialName.takeIf { includeTitle },
                properties = properties,
                required = required.takeIf { it.isNotEmpty() },
                nullable = isNullable,
            )
        }

        PolymorphicKind.SEALED -> {
            if (elementsCount == 2) {
                val discriminatorProperty = getElementName(0)
                val value = getElementDescriptor(1)
                val sealedTypes = (0..<value.elementsCount).associate { i ->
                    value.getElementName(i) to ReferenceOr.schema(value.getElementName(i).substringAfterLast('.')).ref
                }
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT,
                    title = serialName.takeIf { includeTitle },
                    discriminator = JsonSchema.Discriminator(discriminatorProperty, sealedTypes),
                    nullable = isNullable,
                )
            } else {
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT,
                    title = serialName.takeIf { includeTitle },
                    nullable = isNullable,
                )
            }
        }

        StructureKind.LIST -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.ARRAY,
                items = Value(getElementDescriptor(0).buildJsonSchema()),
                nullable = isNullable,
            )
        }

        StructureKind.MAP -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT,
                nullable = isNullable,
                additionalProperties = if (elementsCount > 1) {
                    PSchema(
                        Value(getElementDescriptor(1).buildJsonSchema())
                    )
                } else {
                    Allowed(true)
                },
            )
        }

        PrimitiveKind.STRING,
        PrimitiveKind.CHAR ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.STRING,
                nullable = isNullable,
            )

        PrimitiveKind.BOOLEAN ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.BOOLEAN,
                nullable = isNullable,
            )

        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.INTEGER,
                nullable = isNullable,
            )

        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.NUMBER,
                nullable = isNullable,
            )

        SerialKind.ENUM -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.STRING,
                enum = List(elementsCount) { i -> GenericElement<String>(getElementName(i)) },
                nullable = isNullable,
            )
        }

        PolymorphicKind.OPEN,
        SerialKind.CONTEXTUAL -> {
            // For contextual serializers, we need to get the actual serializer from the context
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT,
                nullable = isNullable,
            )
        }
    }
}

internal fun jsonSchemaFromAnnotations(
    annotations: List<Annotation>,
    reflectSchema: (KClass<*>).() -> ReferenceOr<JsonSchema>,
    type: JsonType,
    title: String? = null,
    required: List<String>? = null,
    items: ReferenceOr<JsonSchema>? = null,
    properties: Map<String, ReferenceOr<JsonSchema>>? = null,
    additionalProperties: AdditionalProperties? = null,
    enum: List<GenericElement?>? = null,
    nullable: Boolean? = null,
    format: String? = null,
    discriminator: JsonSchema.Discriminator? = null,
): JsonSchema {
    fun parseJsonLiteralToGenericElement(text: String): GenericElement {
        val element: JsonElement = Json.parseToJsonElement(text)
        return JsonGenericElement(element, Json, JsonElement.serializer())
    }

    fun parseJsonLiteralOrUseString(text: String): GenericElement? =
        try {
            val element = Json.parseToJsonElement(text)
            JsonGenericElement(element, Json, JsonElement.serializer())
        } catch (_: Exception) {
            GenericElement(text)
        }

    fun refFromString(raw: String): ReferenceOr<JsonSchema> {
        val trimmed = raw.trim()
        // If caller passed a full JSON-pointer-ish ref, keep it; otherwise treat it as a schema component name.
        return if (trimmed.startsWith("#/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Reference(trimmed)
        } else {
            ReferenceOr.schema(trimmed)
        }
    }

    return JsonSchema(
        type = annotations.firstInstanceOf<JsonSchemaType>()?.value ?: type,
        title = annotations.firstInstanceOf<JsonSchemaTitle>()?.value ?: title,
        description = annotations.firstInstanceOf<JsonSchemaDescription>()?.value,
        required =
        annotations.firstInstanceOf<JsonSchemaRequired>()?.value?.toList()?.takeIf { it.isNotEmpty() } ?: required,
        nullable = annotations.firstInstanceOf<JsonSchemaNullable>()?.value ?: nullable?.takeIf { it },
        anyOf = annotations.firstInstanceOf<JsonSchemaAnyOfRefs>()?.value
            ?.map { refFromString(it) }
            ?.takeIf { it.isNotEmpty() },
        oneOf = annotations.firstInstanceOf<JsonSchemaOneOf>()?.value
            ?.map { it.reflectSchema() }
            ?.takeIf { it.isNotEmpty() },
        not = annotations.firstInstanceOf<JsonSchemaNot>()?.value?.reflectSchema(),
        properties = properties,
        additionalProperties =
        annotations.firstInstanceOf<JsonSchemaAdditionalPropertiesRef>()?.value?.let { PSchema(it.reflectSchema()) }
            ?: annotations.firstInstanceOf<JsonSchemaAdditionalPropertiesAllowed>()?.let { Allowed(true) }
            ?: additionalProperties,
        discriminator = annotations.firstInstanceOf<JsonSchemaDiscriminator>()?.let { annotation ->
            JsonSchema.Discriminator(
                annotation.property,
                annotation.mapping.associate {
                    it.key to "#/components/schemas/${it.ref.simpleName}"
                }
            )
        } ?: discriminator,
        readOnly = annotations.firstInstanceOf<JsonSchemaReadOnly>()?.let { true },
        writeOnly = annotations.firstInstanceOf<JsonSchemaWriteOnly>()?.let { true },
        deprecated = annotations.firstInstanceOf<JsonSchemaDeprecated>()?.let { true },
        maxProperties = annotations.firstInstanceOf<JsonSchemaMaxProperties>()?.value,
        minProperties = annotations.firstInstanceOf<JsonSchemaMinProperties>()?.value,
        default = annotations.firstInstanceOf<JsonSchemaDefault>()?.value?.let { parseJsonLiteralToGenericElement(it) },
        format = annotations.firstInstanceOf<JsonSchemaFormat>()?.value ?: format,
        items = annotations.firstInstanceOf<JsonSchemaItemsRef>()?.value?.reflectSchema() ?: items,
        maximum = annotations.firstInstanceOf<JsonSchemaMaximum>()?.value,
        exclusiveMaximum = annotations.firstInstanceOf<JsonSchemaMaximum>()?.exclusive?.takeIf { it },
        minimum = annotations.firstInstanceOf<JsonSchemaMinimum>()?.value,
        exclusiveMinimum = annotations.firstInstanceOf<JsonSchemaMinimum>()?.exclusive?.takeIf { it },
        maxLength = annotations.firstInstanceOf<JsonSchemaMaxLength>()?.value,
        minLength = annotations.firstInstanceOf<JsonSchemaMinLength>()?.value,
        pattern = annotations.firstInstanceOf<JsonSchemaPattern>()?.value,
        maxItems = annotations.firstInstanceOf<JsonSchemaMaxItems>()?.value,
        minItems = annotations.firstInstanceOf<JsonSchemaMinItems>()?.value,
        uniqueItems = annotations.firstInstanceOf<JsonSchemaUniqueItems>()?.let { true },
        enum = annotations.firstInstanceOf<JsonSchemaEnum>()?.value
            ?.map { parseJsonLiteralOrUseString(it) }
            ?.takeIf { it.isNotEmpty() } ?: enum,
        multipleOf = annotations.firstInstanceOf<JsonSchemaMultipleOf>()?.value,
        id = annotations.firstInstanceOf<JsonSchemaId>()?.value,
        anchor = annotations.firstInstanceOf<JsonSchemaAnchor>()?.value,
        recursiveAnchor = annotations.firstInstanceOf<JsonSchemaAnchor>()?.recursive?.takeIf { it },
        example = annotations.firstInstanceOf<JsonSchemaExample>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.singleOrNull(),
        examples = annotations.firstInstanceOf<JsonSchemaExample>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.takeIf { it.size > 1 },
    )
}

private inline fun <reified T : Annotation> List<Annotation>.firstInstanceOf(): T? =
    filterIsInstance<T>().firstOrNull()

private inline fun <reified T : Annotation> Map<KClass<out Annotation>, List<Annotation>>.lookup(): T? =
    this[T::class]?.filterIsInstance<T>()?.firstOrNull()

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
            type = JsonType.OBJECT,
            description = "Failed to resolve schema for ${T::class.simpleName}. ${e::class.simpleName}: ${e.message}"
        )
    }
}
