/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.serialization.json.*

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * @param json format instance (optional)
 * @param contentType to register with, application/json by default
 */
@Deprecated(message = "Please use json function with streamRequestBody parameter", level = DeprecationLevel.HIDDEN)
public actual fun Configuration.json(
    json: Json,
    contentType: ContentType
) {
    json(json, contentType, true)
}

/**
 * Register `application/json` (or another specified [contentType]) content type
 * to [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * @param json format instance (optional)
 * @param contentType to register with, application/json by default
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 */
public fun Configuration.json(
    json: Json = DefaultJson,
    contentType: ContentType = ContentType.Application.Json,
    streamRequestBody: Boolean = true
) {
    register(contentType, KotlinxSerializationJsonJvmConverter(json, streamRequestBody))
}
