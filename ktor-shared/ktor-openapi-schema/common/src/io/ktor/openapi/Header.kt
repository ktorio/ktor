/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.openapi

import io.ktor.utils.io.*
import kotlinx.serialization.Serializable

/**
 * Header fields have the same meaning as for 'Param'. Style is always treated as [Style.simple], as
 * it is the only value allowed for headers.
 */
@Serializable
public data class Header(
    /** A short description of the header. */
    public val description: String? = null,
    public val required: Boolean? = null,
    public val deprecated: Boolean? = null,
    public val allowEmptyValue: Boolean? = null,
    public val explode: Boolean? = null,
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    public val schema: ReferenceOr<Schema>? = null,
) {
    /** Builder for constructing a [Header] instance. */
    @KtorDsl
    public class Builder {
        /** A brief description of the header. */
        public var description: String? = null

        /** Whether this header is mandatory. */
        public var required: Boolean? = null

        /** Marks the header as deprecated when true. */
        public var deprecated: Boolean? = null

        /** Allows sending a header with an empty value. */
        public var allowEmptyValue: Boolean? = null

        /** Specifies whether arrays and objects generate separate parameters for each value. */
        public var explode: Boolean? = null

        /** The schema defining the header type. */
        public var schema: ReferenceOr<Schema>? = null

        /** Map of examples for the header. */
        public var examples: MutableMap<String, ReferenceOr<ExampleObject>> = mutableMapOf()

        /**
         * Adds an example for this header.
         *
         * @param name The example identifier.
         * @param example The example object.
         */
        public fun example(name: String, example: ExampleObject) {
            examples[name] = ReferenceOr.Value(example)
        }

        /** Constructs the [Header]. */
        internal fun build(): Header {
            return Header(
                description = description,
                required = required,
                deprecated = deprecated,
                allowEmptyValue = allowEmptyValue,
                explode = explode,
                examples = examples.ifEmpty { null },
                schema = schema,
            )
        }
    }
}
