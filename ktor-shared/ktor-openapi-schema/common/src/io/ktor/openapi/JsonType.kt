/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.JsonSchema.SchemaType
import io.ktor.openapi.JsonSchema.SchemaType.JsonTypeSerializer
import kotlinx.serialization.Serializable

@Serializable(JsonTypeSerializer::class)
public enum class JsonType : SchemaType {
    ARRAY,
    OBJECT,
    NUMBER,
    BOOLEAN,
    INTEGER,
    NULL,
    STRING
}
