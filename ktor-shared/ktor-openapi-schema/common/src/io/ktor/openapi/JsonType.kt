/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.JsonSchema.SchemaType
import io.ktor.openapi.JsonSchema.SchemaType.JsonTypeSerializer
import kotlinx.serialization.Serializable

/**
 * Represents the base data types defined in the JSON Schema specification.
 *
 * These types are used to constrain the values of properties within a [JsonSchema].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType)
 *
 * @link https://json-schema.org/understanding-json-schema/reference/type.html
 */
@Serializable(JsonTypeSerializer::class)
public enum class JsonType : SchemaType {
    /**
     * An ordered list of instances.
     */
    ARRAY,

    /**
     * An unordered set of properties.
     */
    OBJECT,

    /**
     * Any numeric value, including integers and floating-point numbers.
     */
    NUMBER,

    /**
     * A true or false value.
     */
    BOOLEAN,

    /**
     * A numeric value with no fractional part.
     */
    INTEGER,

    /**
     * A null value.
     */
    NULL,

    /**
     * A sequence of characters.
     */
    STRING
}
