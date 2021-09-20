/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.http.*
import kotlinx.serialization.*

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and binary [format] (such as CBOR, ProtoBuf).
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    serialization(ContentType.Application.Json, Json.nonstrict) {
 *      match(JsonElement.serializer()) {value: Any, module: SerializersModule ->
 *          value is JsonElement
 *      }
 *    }
 *    serialization(ContentType.Application.Cbor, Cbor)
 * }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
public fun <T : BinaryFormat> ContentNegotiation.Config.serialization(
    contentType: ContentType,
    format: T,
    configuration: SerializationConverter<T>.() -> Unit = {},
) {
    register(
        contentType,
        SerializationConverter(format),
        ExtendedContentTypeMatcher(contentType),
        configuration
    )
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and string [format] (such as Json).
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    serialization(ContentType.Application.Json, Json.nonstrict) {
 *      match(JsonElement.serializer()) {value: Any, module: SerializersModule ->
 *          value is JsonElement
 *      }
 *    }
 *    serialization(ContentType.Application.Cbor, Cbor)
 * }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
public fun <T : StringFormat> ContentNegotiation.Config.serialization(
    contentType: ContentType,
    format: T,
    configuration: SerializationConverter<T>.() -> Unit = {},
) {
    register(
        contentType,
        SerializationConverter(format),
        ExtendedContentTypeMatcher(contentType),
        configuration
    )
}
