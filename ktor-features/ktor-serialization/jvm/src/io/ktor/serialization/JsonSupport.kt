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
 * See [JsonConfiguration] for more details.
 */
val DefaultJsonConfiguration: JsonConfiguration = JsonConfiguration.Stable.copy(
    encodeDefaults = true,
    strictMode = false,
    unquoted = false,
    prettyPrint = false,
    useArrayPolymorphism = true
)

fun ContentNegotiation.Configuration.json(
    json: JsonConfiguration = DefaultJsonConfiguration,
    module: SerialModule = EmptyModule,
    contentType: ContentType = ContentType.Application.Json
) {
    json(Json(json, module), contentType)
}

fun ContentNegotiation.Configuration.json(
    json: Json = Json(DefaultJsonConfiguration),
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json as StringFormat)
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Suppress("unused")
@Deprecated("Use json instead", ReplaceWith("json()"))
fun ContentNegotiation.Configuration.serialization() {
    json()
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Deprecated("Use json function instead.", ReplaceWith("json(contentType = contentType)"))
fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType
) {
    json(contentType = contentType)
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
@Suppress("CONFLICTING_OVERLOADS") // conflict with hidden declaration
@Deprecated("Use json function instead.", ReplaceWith("json(json, contentType)"))
fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType,
    json: Json
) {
    json(json, contentType)
}

