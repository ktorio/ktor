/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.shared.serialization.*
import io.ktor.shared.serialization.kotlinx.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

/**
 * The default json configuration used in [KotlinxSerializationConverter]. The settings are:
 * - defaults are serialized
 * - mode is not strict so extra json fields are ignored
 * - pretty printing is disabled
 * - array polymorphism is enabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [Json] for more details.
 */
public val DefaultJson: Json = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = false
}

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * @param json format instance (optional)
 * @param contentType to register with, application/json by default
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.json(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json
) {
    register(
        contentType,
        KotlinxSerializationConverter(json, mapOf(JsonElement.serializer() to { it is JsonElement }))
    )
}
