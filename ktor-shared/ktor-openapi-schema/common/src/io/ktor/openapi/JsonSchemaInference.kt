/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.AdditionalProperties.*
import io.ktor.openapi.JsonSchema.*
import io.ktor.openapi.ReferenceOr.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Context interface for creating schema from type metadata.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonSchemaInference)
 */
public fun interface JsonSchemaInference {
    /**
     * Builds a [JsonSchema] for the given [type].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonSchemaInference.buildSchema)
     */
    public fun buildSchema(type: KType): JsonSchema
}

/**
 * Infers JSON schema from kotlinx-serialization descriptors.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.KotlinxJsonSchemaInference)
 */
public val KotlinxJsonSchemaInference: JsonSchemaInference = JsonSchemaInference { type ->
    serializer(type)
        .descriptor
        .buildJsonSchema(
            // parameterized types cannot be referenced from their serial name
            includeTitle = type.arguments.isEmpty(),
            visiting = mutableSetOf()
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.buildJsonSchema)
 *
 * @return A [JsonSchema] object representing the JSON Schema for this descriptor.
 *
 * Note: This function does not handle circular references. For types with circular dependencies,
 * consider implementing depth tracking or schema references to avoid stack overflow.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class, InternalAPI::class)
public fun SerialDescriptor.buildJsonSchema(
    includeTitle: Boolean = true,
    includeAnnotations: List<Annotation> = emptyList(),
    visiting: MutableSet<String>,
): JsonSchema {
    val reflectJsonSchema: KClass<*>.() -> ReferenceOr<JsonSchema> = {
        Value(this.serializer().descriptor.buildJsonSchema(includeTitle, visiting = visiting))
    }
    val annotations = includeAnnotations + annotations

    // For inline descriptors, use the delegate descriptor
    if (this.isInline) {
        return this.getElementDescriptor(0)
            .buildJsonSchema(
                visiting = visiting,
                includeAnnotations = includeAnnotations
            )
    }

    return when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            visiting += nonNullSerialName

            val properties = mutableMapOf<String, ReferenceOr<JsonSchema>>()
            val required = mutableListOf<String>()

            for (i in 0 until elementsCount) {
                val name = getElementName(i)
                val elementDescriptor = getElementDescriptor(i)
                val elementName = elementDescriptor.nonNullSerialName
                val annotations = getElementAnnotations(i)
                if (annotations.any { it is Ignore }) continue

                if (!isElementOptional(i) && !elementDescriptor.isNullable) {
                    required.add(name)
                }

                try {
                    // recursion guard
                    properties[name] = if (!visiting.add(elementName)) {
                        ReferenceOr.schema(elementName)
                            .nonNullable(elementDescriptor.isNullable)
                    } else {
                        Value(
                            elementDescriptor.buildJsonSchema(
                                includeAnnotations = getElementAnnotations(i),
                                visiting = visiting,
                            )
                        )
                    }
                } finally {
                    visiting.remove(elementName)
                }
            }
            visiting.remove(nonNullSerialName)

            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT,
                title = nonNullSerialName.takeIf { includeTitle },
                properties = properties,
                required = required.takeIf { it.isNotEmpty() },
            ).nonNullable(isNullable)
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
                    type = JsonType.OBJECT.orNullable(isNullable),
                    title = serialName.takeIf { includeTitle },
                    discriminator = JsonSchemaDiscriminator(discriminatorProperty, sealedTypes),
                )
            } else {
                jsonSchemaFromAnnotations(
                    annotations = annotations,
                    reflectSchema = reflectJsonSchema,
                    type = JsonType.OBJECT.orNullable(isNullable),
                    title = serialName.takeIf { includeTitle },
                )
            }
        }

        StructureKind.LIST -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.ARRAY.orNullable(isNullable),
                items = Value(getElementDescriptor(0).buildJsonSchema(visiting = visiting)),
            )
        }

        StructureKind.MAP -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT.orNullable(isNullable),
                additionalProperties = if (elementsCount > 1) {
                    PSchema(
                        Value(getElementDescriptor(1).buildJsonSchema(visiting = visiting))
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
                type = JsonType.STRING.orNullable(isNullable),
            )

        PrimitiveKind.BOOLEAN ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.BOOLEAN.orNullable(isNullable),
            )

        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.INTEGER.orNullable(isNullable),
            )

        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.NUMBER.orNullable(isNullable),
            )

        SerialKind.ENUM -> {
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.STRING.orNullable(isNullable),
                enum = List(elementsCount) { i -> GenericElement<String>(getElementName(i)) },
            )
        }

        PolymorphicKind.OPEN,
        SerialKind.CONTEXTUAL -> {
            // For contextual serializers, we need to get the actual serializer from the context
            jsonSchemaFromAnnotations(
                annotations = annotations,
                reflectSchema = reflectJsonSchema,
                type = JsonType.OBJECT.orNullable(isNullable),
            )
        }
    }
}

private val SerialDescriptor.nonNullSerialName get() = serialName.trimEnd('?')

@InternalAPI
public fun ReferenceOr<JsonSchema>.nonNullable(isNullable: Boolean): ReferenceOr<JsonSchema> =
    if (isNullable) {
        Value(
            JsonSchema(
                oneOf = listOf(
                    this,
                    Value(JsonSchema(type = JsonType.NULL),)
                )
            )
        )
    } else {
        this
    }

@InternalAPI
public fun JsonSchema.nonNullable(isNullable: Boolean): JsonSchema =
    if (isNullable) {
        JsonSchema(
            oneOf = listOf(
                Value(this),
                Value(JsonSchema(type = JsonType.NULL),)
            )
        )
    } else {
        this
    }

@InternalAPI
public fun JsonType.orNullable(isNullable: Boolean): SchemaType =
    if (isNullable) SchemaType.AnyOf(listOf(this, JsonType.NULL)) else this

/**
 * Create an instance of JsonSchema from the provided properties and supplied annotations.
 *
 * This is used internally for different inference strategies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.jsonSchemaFromAnnotations)
 */
@InternalAPI
public fun jsonSchemaFromAnnotations(
    annotations: List<Annotation>,
    reflectSchema: (KClass<*>).() -> ReferenceOr<JsonSchema>,
    type: SchemaType,
    title: String? = null,
    required: List<String>? = null,
    items: ReferenceOr<JsonSchema>? = null,
    properties: Map<String, ReferenceOr<JsonSchema>>? = null,
    additionalProperties: AdditionalProperties? = null,
    enum: List<GenericElement?>? = null,
    format: String? = null,
    discriminator: JsonSchemaDiscriminator? = null,
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
        type = annotations.firstInstanceOf<Type>()?.value ?: type,
        title = annotations.firstInstanceOf<Title>()?.value ?: title,
        description = annotations.firstInstanceOf<Description>()?.value,
        required =
        annotations.firstInstanceOf<JsonSchema.Required>()?.value?.toList()?.takeIf { it.isNotEmpty() }
            ?: required,
        anyOf = annotations.firstInstanceOf<AnyOfRefs>()?.value
            ?.map { refFromString(it) }
            ?.takeIf { it.isNotEmpty() },
        oneOf = annotations.firstInstanceOf<OneOf>()?.value
            ?.map { it.reflectSchema() }
            ?.takeIf { it.isNotEmpty() },
        not = annotations.firstInstanceOf<Not>()?.value?.reflectSchema(),
        properties = properties,
        additionalProperties =
        annotations.firstInstanceOf<AdditionalPropertiesRef>()?.value?.let { PSchema(it.reflectSchema()) }
            ?: annotations.firstInstanceOf<AdditionalPropertiesAllowed>()?.let { Allowed(true) }
            ?: additionalProperties,
        discriminator = annotations.firstInstanceOf<Discriminator>()?.let { annotation ->
            JsonSchemaDiscriminator(
                annotation.property,
                annotation.mapping.associate {
                    it.key to "#/components/schemas/${it.ref.simpleName}"
                }
            )
        } ?: discriminator,
        readOnly = annotations.firstInstanceOf<ReadOnly>()?.let { true },
        writeOnly = annotations.firstInstanceOf<WriteOnly>()?.let { true },
        deprecated = annotations.firstInstanceOf<DeprecatedSchema>()?.let { true },
        maxProperties = annotations.firstInstanceOf<MaxProperties>()?.value,
        minProperties = annotations.firstInstanceOf<MinProperties>()?.value,
        default = annotations.firstInstanceOf<Default>()?.value?.let { parseJsonLiteralToGenericElement(it) },
        format = annotations.firstInstanceOf<Format>()?.value ?: format,
        items = annotations.firstInstanceOf<ItemsRef>()?.value?.reflectSchema() ?: items,
        maximum = annotations.firstInstanceOf<Maximum>()?.takeIf { !it.exclusive }?.value,
        exclusiveMaximum = annotations.firstInstanceOf<Maximum>()?.takeIf { it.exclusive }?.value,
        minimum = annotations.firstInstanceOf<Minimum>()?.takeIf { !it.exclusive }?.value,
        exclusiveMinimum = annotations.firstInstanceOf<Minimum>()?.takeIf { it.exclusive }?.value,
        maxLength = annotations.firstInstanceOf<MaxLength>()?.value,
        minLength = annotations.firstInstanceOf<MinLength>()?.value,
        pattern = annotations.firstInstanceOf<Pattern>()?.value,
        maxItems = annotations.firstInstanceOf<MaxItems>()?.value,
        minItems = annotations.firstInstanceOf<MinItems>()?.value,
        uniqueItems = annotations.firstInstanceOf<UniqueItems>()?.let { true },
        enum = annotations.firstInstanceOf<JsonSchema.Enum>()?.value
            ?.map { parseJsonLiteralOrUseString(it) }
            ?.takeIf { it.isNotEmpty() } ?: enum,
        multipleOf = annotations.firstInstanceOf<MultipleOf>()?.value,
        id = annotations.firstInstanceOf<Id>()?.value,
        anchor = annotations.firstInstanceOf<Anchor>()?.value,
        dynamicAnchor = annotations.firstInstanceOf<Anchor>()?.dynamic?.takeIf { it },
        example = annotations.firstInstanceOf<Example>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.singleOrNull(),
        examples = annotations.firstInstanceOf<Example>()?.value
            ?.map { parseJsonLiteralToGenericElement(it) }
            ?.takeIf { it.isNotEmpty() }?.takeIf { it.size > 1 },
    )
}

private inline fun <reified T : Annotation> List<Annotation>.firstInstanceOf(): T? =
    filterIsInstance<T>().firstOrNull()

/**
 * Generates a JSON Schema representation for the given type [T].
 *
 * This function attempts to infer the schema from the type's [KSerializer].
 * If the type is not serializable, returns a Schema representing a non-serializable type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.jsonSchema)
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
