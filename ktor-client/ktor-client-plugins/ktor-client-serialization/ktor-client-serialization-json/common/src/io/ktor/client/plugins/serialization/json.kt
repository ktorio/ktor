/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.serialization

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Register kotlinx.serialization JSON converter into [ContentNegotiation] feature
 * with the specified [contentType].
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    json(Json) {
 *      match(MyCustomSerializer()) { value: Any ->
 *          value == someCondition
 *      }
 *    }
 * }
 * ```
 */
public fun <T : Json> ContentNegotiation.Config.json(
    format: T,
    contentType: ContentType = ContentType.Application.Json,
    configuration: SerializationConverter<T>.() -> Unit = {},
) {
    serialization(
        contentType,
        format
    ) {
        match(JsonElement.serializer()) { value ->
            value is JsonElement
        }
        configuration()
    }
}

/**
 * Register kotlinx.serialization JSON converter into [ContentNegotiation] feature
 * with the specified [contentType].
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    json {
 *      match(MyCustomSerializer()) {value: Any ->
 *          value == someCondition
 *      }
 *    }
 * }
 * ```
 */
public fun ContentNegotiation.Config.json(
    contentType: ContentType = ContentType.Application.Json,
    configuration: SerializationConverter<Json>.() -> Unit = {},
) {
    json(Json, contentType, configuration)
}
