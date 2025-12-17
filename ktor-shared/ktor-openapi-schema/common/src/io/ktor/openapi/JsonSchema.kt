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
    @Serializable
    public data class Discriminator(
        val propertyName: String,
        val mapping: Map<String, String>? = null,
    )

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
}
