/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.JsonSchema.*
import io.ktor.openapi.JsonSchema.SchemaType.*
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.ARRAY)
     */
    ARRAY,

    /**
     * An unordered set of properties.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.OBJECT)
     */
    OBJECT,

    /**
     * Any numeric value, including integers and floating-point numbers.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.NUMBER)
     */
    NUMBER,

    /**
     * A true or false value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.BOOLEAN)
     */
    BOOLEAN,

    /**
     * A numeric value with no fractional part.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.INTEGER)
     */
    INTEGER,

    /**
     * A null value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.NULL)
     */
    NULL,

    /**
     * A sequence of characters.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.JsonType.STRING)
     */
    STRING
}
