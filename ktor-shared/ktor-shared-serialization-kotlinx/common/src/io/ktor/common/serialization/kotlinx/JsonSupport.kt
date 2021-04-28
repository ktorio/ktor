/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.common.serialization.kotlinx

import io.ktor.common.serialization.*
import io.ktor.http.*
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
 * to [ContentNegotiation] feature using kotlinx.serialization.
 *
 * @param json format instance (optional)
 * @param contentType to register with, application/json by default
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.json(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json
) {
    serialization(contentType, json as StringFormat)
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and binary [format] (such as CBOR, ProtoBuf)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.serialization(
    contentType: ContentType,
    format: BinaryFormat
) {
    register(
        contentType,
        KotlinxSerializationConverter(format)
    )
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and string [format] (such as Json)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.serialization(
    contentType: ContentType,
    format: StringFormat
) {
    register(
        contentType,
        KotlinxSerializationConverter(format)
    )
}
