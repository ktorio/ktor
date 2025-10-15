/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/**
 * Example Object (OpenAPI). Represents an example payload/value for a schema.
 *
 * Notes:
 * - Only one of [value] or [externalValue] should be present (per OpenAPI).
 * - [extensions] carries vendor-specific fields whose keys start with "x-".
 */
@Serializable(ExampleObject.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class ExampleObject(
    /** Short description for the example. */
    public val summary: String? = null,
    /** Long description for the example. CommonMark MAY be used. */
    public val description: String? = null,
    /** Embedded example value. */
    public val value: GenericElement? = null,
    /** URL that points to an external example value. */
    public val externalValue: String? = null,
    /** Specification extensions (keys must start with x-). */
    public val extensions: Map<String, GenericElement> = emptyMap(),
) {
    public companion object {
        internal object Serializer : SerializerWithExtensions<ExampleObject>(
            generatedSerializer(),
            { ex, extensions -> ex.copy(extensions = extensions) }
        )
    }
}
