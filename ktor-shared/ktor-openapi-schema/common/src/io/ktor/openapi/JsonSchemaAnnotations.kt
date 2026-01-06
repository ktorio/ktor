/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
public annotation class JsonSchemaIgnore

/** $id */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
public annotation class JsonSchemaId(val value: String)

/** $anchor */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
public annotation class JsonSchemaAnchor(val value: String, val recursive: Boolean = false)

/** title */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
public annotation class JsonSchemaTitle(val value: String)

/** description */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaDescription(val value: String)

/** type (e.g. "string", "object", "array") */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaType(val value: JsonType)

/** format (e.g. "date-time", "uuid") */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaFormat(val value: String)

/** nullable (OpenAPI-style extension in this project) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaNullable(val value: Boolean = true)

/** deprecated */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaDeprecated

/** readOnly */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaReadOnly

/** writeOnly */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaWriteOnly

/** default (JSON literal as text) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaDefault(val value: String)

/** example (JSON literal as text) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaExample(vararg val value: String)

/** enum (each entry is a JSON literal as text) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaEnum(vararg val value: String)

/** minLength */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMinLength(val value: Int)

/** maxLength */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMaxLength(val value: Int)

/** pattern */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaPattern(val value: String)

/** minimum (+ optional exclusivity) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMinimum(
    val value: Double,
    val exclusive: Boolean = false,
)

/** maximum (+ optional exclusivity) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMaximum(
    val value: Double,
    val exclusive: Boolean = false,
)

/** multipleOf */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMultipleOf(val value: Double)

/** minItems */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMinItems(val value: Int)

/** maxItems */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMaxItems(val value: Int)

/** uniqueItems */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaUniqueItems

/** minProperties */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMinProperties(val value: Int)

/** maxProperties */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaMaxProperties(val value: Int)

/** required (property names) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaRequired(vararg val value: String)

/**
 * additionalProperties (boolean form).
 * If you need schema-valued additionalProperties, use [JsonSchemaAdditionalPropertiesRef].
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaAdditionalPropertiesAllowed

/** additionalProperties (class references) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaAdditionalPropertiesRef(val value: KClass<*>)

/** anyOf (refs) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaAnyOfRefs(vararg val value: String)

/** oneOf (refs) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaOneOf(vararg val value: KClass<*>)

/** not (ref) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaNot(val value: KClass<*>)

/** items (ref) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaItemsRef(val value: KClass<*>)

/** discriminator mappings (OpenAPI-style) */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class JsonSchemaDiscriminator(val property: String, vararg val mapping: Mapping) {
    /**
     * Key-value pair for discriminator mappings (name -> $ref / schema id).
     */
    public annotation class Mapping(
        val key: String, // discriminator value
        val ref: KClass<*>, // target schema $ref (or other identifier your generator understands)
    )
}
