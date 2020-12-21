/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.features.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

/**
 * The default json configuration used in [SerializationConverter]. The settings are:
 * - defaults are serialized
 * - mode is not strict so extra json fields are ignored
 * - pretty printing is disabled
 * - array polymorphism is enabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [Json] for more details.
 */
@Deprecated(
    level = DeprecationLevel.ERROR,
    message = "JsonConfiguration is deprecated, consider using DefaultJson instead.",
    replaceWith = ReplaceWith("DefaultJson")
)
@Suppress("unused")
public val DefaultJsonConfiguration: Json = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = true
}

/**
 * Applies the default json configuration for Ktor to this [JsonBuilder].
 * @see DefaultJson
 */
public fun JsonBuilder.setKtorDefaults() {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = true
}

/**
 * The default json configuration used in [SerializationConverter]. The settings are:
 * - defaults are serialized
 * - mode is not strict so extra json fields are ignored
 * - pretty printing is disabled
 * - array polymorphism is enabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [Json] for more details.
 */
public val DefaultJson: Json = Json {
    setKtorDefaults()
}

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] feature using kotlinx.serialization.
 *
 * @param json configuration with settings such as quoting, pretty print and so on (optional)
 * @param module is used for serialization (optional)
 * @param contentType to register with, application/json by default
 */
@Deprecated(
    level = DeprecationLevel.ERROR,
    message = "JsonConfiguration is deprecated, consider using `Json { serializersModule = module }` instead.",
    replaceWith = ReplaceWith("json(Json { serializersModule = module }, contentType)")
)
@OptIn(ExperimentalSerializationApi::class)
@Suppress("unused", "UNUSED_PARAMETER")
public fun ContentNegotiation.Configuration.json(
    json: Json = Json.Default,
    module: SerializersModule = EmptySerializersModule,
    contentType: ContentType = ContentType.Application.Json
) {
    TODO()
}

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] feature using kotlinx.serialization.
 *
 * @param json format instance (optional)
 * @param contentType to register with, application/json by default
 */
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiation.Configuration.json(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json as StringFormat)
}

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] feature using kotlinx.serialization.
 *
 * @param contentType to register with, application/json by default
 * @param applyDefaultConfiguration specifies whether the standard configuration for ktor should be applied
 * @param jsonBuilder to configure the json instance
 */
@OptIn(ExperimentalSerializationApi::class)
public inline fun ContentNegotiation.Configuration.json(
    contentType: ContentType = ContentType.Application.Json,
    applyDefaultConfiguration: Boolean = true,
    crossinline jsonBuilder: JsonBuilder.() -> Unit
) {
    json(
        Json {
            if (applyDefaultConfiguration)
                setKtorDefaults()
            jsonBuilder()
        },
        contentType
    )
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Suppress("unused")
@Deprecated("Use json instead", ReplaceWith("json()"))
public fun ContentNegotiation.Configuration.serialization() {
    json()
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Deprecated("Use json function instead.", ReplaceWith("json(contentType = contentType)"))
public fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType
) {
    json(contentType = contentType)
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Deprecated("Use json function instead.", ReplaceWith("json(json, contentType)"))
public fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType,
    json: Json
) {
    json(json, contentType)
}

