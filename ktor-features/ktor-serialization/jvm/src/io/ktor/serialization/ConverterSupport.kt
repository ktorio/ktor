/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.serialization

import io.ktor.features.*
import io.ktor.http.*
import kotlinx.serialization.*

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and binary [format] (such as CBOR, ProtoBuf)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType,
    format: BinaryFormat
) {
    register(
        contentType,
        SerializationConverter(format)
    )
}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 * with the specified [contentType] and string [format] (such as Json)
 */
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType,
    format: StringFormat
) {
    register(
        contentType,
        SerializationConverter(format)
    )
}
