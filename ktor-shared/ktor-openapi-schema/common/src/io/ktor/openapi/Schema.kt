/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * The Schema Object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. This object is an extended subset of the
 * [JSON Schema Specification Wright Draft 00](https://json-schema.org/). For more information about
 * the properties, see [JSON Schema Core](https://tools.ietf.org/html/draft-wright-json-schema-00)
 * and [JSON Schema Validation](https://tools.ietf.org/html/draft-wright-json-schema-validation-00).
 * Unless stated otherwise, the property definitions follow the JSON Schema.
 */
@Serializable
public data class Schema(
    val title: String? = null,
    val description: String? = null,
    /** required is an object-level attribute, not a property attribute. */
    val required: List<String> = emptyList(),
    val nullable: Boolean? = null,
    val allOf: List<ReferenceOr<Schema>>? = null,
    val oneOf: List<ReferenceOr<Schema>>? = null,
    val not: ReferenceOr<Schema>? = null,
    val anyOf: List<ReferenceOr<Schema>>? = null,
    val properties: Map<String, ReferenceOr<Schema>>? = null,
    val additionalProperties: AdditionalProperties? = null,
    val discriminator: Discriminator? = null,
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
    val type: @Serializable(SchemaType.Serializer::class) SchemaType? = null,
    val format: String? = null,
    val items: ReferenceOr<Schema>? = null,
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
    val enum: List<String?>? = null,
    val multipleOf: Double? = null,
    @SerialName("\$id") val id: String? = null,
    @SerialName("\$anchor") val anchor: String? = null,
    @SerialName("\$recursiveAnchor") val recursiveAnchor: Boolean? = null,
) {

    @Serializable
    public data class Discriminator(
        val propertyName: String,
        val mapping: Map<String, String>? = null,
    )

    @Serializable(with = SchemaType.Serializer::class)
    public sealed interface SchemaType {

        public data class AnyOf(val types: List<JsonType>) : SchemaType

        public enum class JsonType : SchemaType {
            @SerialName("array") Array,

            @SerialName("object") Object,

            @SerialName("number") Number,

            @SerialName("boolean") Boolean,

            @SerialName("integer") Integer,

            @SerialName("null") Null,

            @SerialName("string") String
        }

        public object Serializer : KSerializer<SchemaType> {
            @OptIn(InternalSerializationApi::class)
            override val descriptor: SerialDescriptor =
                buildSerialDescriptor("io.ktor.openapi.Schema.SchemaType", SerialKind.CONTEXTUAL)

            override fun deserialize(decoder: Decoder): SchemaType {
                val element: GenericElement = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
                val jsonTypeSerializer: KSerializer<JsonType> = decoder.serializersModule.serializer()
                return when {
                    element.isArray() -> AnyOf(
                        element.deserialize(ListSerializer(jsonTypeSerializer))
                    )
                    else -> element.deserialize(jsonTypeSerializer)
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
    }
}
