/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.native.concurrent.*

/**
 * The default JSON configuration used in [KotlinxSerializationConverter]. The settings are:
 * - defaults are serialized
 * - pretty printing is disabled
 * - array polymorphism is disabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [Json] for more details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.kotlinx.json.DefaultJson)
 */

public val DefaultJson: Json =
    Json {
        encodeDefaults = true
        isLenient = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        prettyPrint = false
        useArrayPolymorphism = false
    }

/**
 * Registers the `application/json` (or another specified [contentType]) content type
 * to the [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * The example below shows how to register the JSON serializer with
 * customized serialization settings provided by JsonBuilder:
 * ```kotlin
 * install(ContentNegotiation) {
 *     json(Json {
 *         prettyPrint = true
 *         isLenient = true
 *     })
 * }
 * ```
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.kotlinx.json.json)
 *
 * @param json a format instance (optional)
 * @param contentType to register with, `application/json` by default
 */
public fun Configuration.json(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json)
}

/**
 * Registers the `application/json` (or another specified [contentType]) content type
 * to the [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * This uses the experimental JSON support for kotlinx-io to stream content more efficiently.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.kotlinx.json.jsonIo)
 *
 * @param json A JSON instance used for serialization and deserialization. Defaults to an instance of DefaultJson.
 * @param contentType The content type to be associated with the JSON converter. Defaults to ContentType.Application.Json.
 */
@ExperimentalSerializationApi
public fun Configuration.jsonIo(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json
) {
    register(contentType, ExperimentalJsonConverter(json))
}
