/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

/**
 * The Schema Object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. This object is an extended subset of the
 * [JSON Schema Specification Wright Draft 00](https://json-schema.org/). For more information about
 * the properties, see [JSON Schema Core](https://tools.ietf.org/html/draft-wright-json-schema-00)
 * and [JSON Schema Validation](https://tools.ietf.org/html/draft-wright-json-schema-validation-00).
 * Unless stated otherwise, the property definitions follow the JSON Schema.
 */
@Serializable
public data class JsonSchema(
    val type: @Serializable(SchemaType.Serializer::class) SchemaType? = null,
    val title: String? = null,
    val description: String? = null,
    val required: List<String>? = null,
    val nullable: Boolean? = null,
    val allOf: List<ReferenceOr<JsonSchema>>? = null,
    val oneOf: List<ReferenceOr<JsonSchema>>? = null,
    val not: ReferenceOr<JsonSchema>? = null,
    val anyOf: List<ReferenceOr<JsonSchema>>? = null,
    val properties: Map<String, ReferenceOr<JsonSchema>>? = null,
    val additionalProperties: AdditionalProperties? = null,
    val discriminator: JsonSchemaDiscriminator? = null,
    val readOnly: Boolean? = null,
    val writeOnly: Boolean? = null,
    val xml: Xml? = null,
    val externalDocs: ExternalDocs? = null,
    val example: GenericElement? = null,
    val examples: List<GenericElement>? = null,
    val deprecated: Boolean? = null,
    val maxProperties: Int? = null,
    val minProperties: Int? = null,
    /** Unlike JSON Schema this value MUST conform to the defined type for this parameter. */
    val default: GenericElement? = null,
    val format: String? = null,
    val items: ReferenceOr<JsonSchema>? = null,
    val maximum: Double? = null,
    val exclusiveMaximum: Boolean? = null,
    val minimum: Double? = null,
    val exclusiveMinimum: Boolean? = null,
    val maxLength: Int? = null,
    val minLength: Int? = null,
    val pattern: String? = null,
    val maxItems: Int? = null,
    val minItems: Int? = null,
    val uniqueItems: Boolean? = null,
    // inner type nullable to allow explicit "null" value for nullable enums
    val enum: List<GenericElement?>? = null,
    val multipleOf: Double? = null,
    @SerialName("\$id") val id: String? = null,
    @SerialName("\$anchor") val anchor: String? = null,
    @SerialName("\$recursiveAnchor") val recursiveAnchor: Boolean? = null,
) {

    /**
     * Represents a sealed interface for defining schema types in a JSON structure.
     *
     * This interface serves as the base type for different schema representations, allowing components
     * to handle various schema variations, such as combinations of multiple types or individual JSON types.
     *
     * Implementing Types:
     * - [AnyOf]: Represents a type that is a combination of multiple JSON types.
     * - [JsonType]: Enum representing standard JSON types (ARRAY, OBJECT, NUMBER, BOOLEAN, INTEGER, NULL, STRING).
     *
     * Serialization and Deserialization:
     * - Custom serialization and deserialization are handled using the [Serializer] object.
     * - Deserialization parses a [GenericElement], which may represent different JSON structures.
     * - Serialization encodes the schema type into a compatible JSON format.
     */
    @Serializable(with = SchemaType.Serializer::class)
    public sealed interface SchemaType {

        @Serializable
        public data class AnyOf(val types: List<JsonType>) : SchemaType

        public object Serializer : KSerializer<SchemaType> {
            @OptIn(InternalSerializationApi::class)
            override val descriptor: SerialDescriptor =
                buildSerialDescriptor("io.ktor.openapi.Schema.SchemaType", SerialKind.CONTEXTUAL)

            override fun deserialize(decoder: Decoder): SchemaType {
                val element: GenericElement = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
                return when {
                    element.isArray() -> AnyOf(element.deserialize(ListSerializer(JsonTypeSerializer)))
                    else -> element.deserialize(JsonTypeSerializer)
                }
            }

            override fun serialize(encoder: Encoder, value: SchemaType) {
                when (value) {
                    is AnyOf ->
                        encoder.encodeSerializableValue(
                            ListSerializer(String.serializer()),
                            value.types.map { it.name.lowercase() },
                        )

                    is JsonType -> encoder.encodeString(value.name.lowercase())
                }
            }
        }

        public object JsonTypeSerializer : KSerializer<JsonType> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JsonType", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: JsonType) {
                encoder.encodeString(value.name.lowercase())
            }

            override fun deserialize(decoder: Decoder): JsonType {
                return JsonType.valueOf(decoder.decodeString().uppercase())
            }
        }
    }

    // Annotations scoped under JsonSchema for convenience / simplified naming

    /** Skips the property from the inferred JSON schema model */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.PROPERTY)
    public annotation class Ignore

    /** $id */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS)
    public annotation class Id(val value: String)

    /** $anchor */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS)
    public annotation class Anchor(val value: String, val recursive: Boolean = false)

    /** title */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS)
    public annotation class Title(val value: String)

    /** description */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Description(val value: String)

    /** type (e.g. "string", "object", "array") */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Type(val value: JsonType)

    /** format (e.g. "date-time", "uuid") */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Format(val value: String)

    /** nullable (OpenAPI-style extension in this project) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Nullable(val value: Boolean = true)

    /** deprecated */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class DeprecatedSchema

    /** readOnly */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class ReadOnly

    /** writeOnly */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class WriteOnly

    /** default (JSON literal as text) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Default(val value: String)

    /** example (JSON literal as text) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Example(vararg val value: String)

    /** enum (each entry is a JSON literal as text) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Enum(vararg val value: String)

    /** minLength */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MinLength(val value: Int)

    /** maxLength */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MaxLength(val value: Int)

    /** pattern */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Pattern(val value: String)

    /** minimum (+ optional exclusivity) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Minimum(
        val value: Double,
        val exclusive: Boolean = false,
    )

    /** maximum (+ optional exclusivity) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Maximum(
        val value: Double,
        val exclusive: Boolean = false,
    )

    /** multipleOf */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MultipleOf(val value: Double)

    /** minItems */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MinItems(val value: Int)

    /** maxItems */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MaxItems(val value: Int)

    /** uniqueItems */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class UniqueItems

    /** minProperties */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MinProperties(val value: Int)

    /** maxProperties */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class MaxProperties(val value: Int)

    /** required (property names) */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Required(vararg val value: String)

    /**
     * Sets `additionalProperties` to `true` or `false`.
     * Use [AdditionalPropertiesRef] if you need to define a schema for additional properties.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class AdditionalPropertiesAllowed

    /**
     * Defines the schema for any additional properties not explicitly listed in `properties`.
     *
     * @param value The Kotlin class to use as the schema for additional properties.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class AdditionalPropertiesRef(val value: KClass<*>)

    /**
     * Validates the data against any of the provided schema references.
     *
     * @param value Variadic array of schema identifiers or references.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class AnyOfRefs(vararg val value: String)

    /**
     * Validates the data against exactly one of the provided schemas.
     *
     * @param value Variadic array of Kotlin classes representing the possible schemas.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class OneOf(vararg val value: KClass<*>)

    /**
     * Ensures the data does not match the provided schema.
     *
     * @param value The Kotlin class representing the schema to negate.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Not(val value: KClass<*>)

    /**
     * Defines the schema for items within an array.
     *
     * @param value The Kotlin class representing the item schema.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class ItemsRef(val value: KClass<*>)

    /**
     * Configures polymorphism using a discriminator property and optional explicit mappings.
     *
     * @property property The name of the property in the payload used to identify the type.
     * @property mapping An array of [Mapping] annotations defining the relationship between
     * discriminator values and schema classes.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @SerialInfo
    @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
    public annotation class Discriminator(val property: String, vararg val mapping: Mapping) {
        /**
         * Key-value pair for discriminator mappings (name -> $ref / schema id).
         */
        public annotation class Mapping(
            val key: String, // discriminator value
            val ref: KClass<*>, // target schema $ref (or other identifier your generator understands)
        )
    }
}

/**
 * Adds support for polymorphism. The discriminator is the schema property name that is used to
 * differentiate between other schema that inherit this schema. The property name used MUST be defined
 * at this schema and it MUST be in the required property list. When used, the value MUST be the name of
 * this schema or any schema that inherits it.
 */
@Serializable
public data class JsonSchemaDiscriminator(
    val propertyName: String,
    val mapping: Map<String, String>? = null,
)
