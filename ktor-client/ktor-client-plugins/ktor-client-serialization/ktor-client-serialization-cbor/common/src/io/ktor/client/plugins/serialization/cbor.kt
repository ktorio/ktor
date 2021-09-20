/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.serialization

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*

/**
 * Register kotlinx.serialization CBOR converter into [ContentNegotiation] feature
 * with the specified [contentType].
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    cbor(Cbor) {
 *      match(MyCustomSerializer()) { value: Any ->
 *          value == someCondition
 *      }
 *    }
 * }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
public fun <T : Cbor> ContentNegotiation.Config.cbor(
    format: T,
    contentType: ContentType = ContentType.Application.Cbor,
    configuration: SerializationConverter<T>.() -> Unit = {},
) {
    serialization(
        contentType,
        format,
        configuration
    )
}

/**
 * Register kotlinx.serialization CBOR converter into [ContentNegotiation] feature
 * with the specified [contentType].
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    cbor {
 *      match(MyCustomSerializer()) { value: Any ->
 *          value == someCondition
 *      }
 *    }
 * }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
public fun ContentNegotiation.Config.cbor(
    contentType: ContentType = ContentType.Application.Cbor,
    configuration: SerializationConverter<Cbor>.() -> Unit = {},
) {
    cbor(Cbor, contentType, configuration)
}
