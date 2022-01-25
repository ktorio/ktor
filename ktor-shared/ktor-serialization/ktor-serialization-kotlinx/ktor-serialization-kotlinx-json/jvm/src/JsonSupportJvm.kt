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
public actual fun Configuration.json(
    json: Json,
    contentType: ContentType
) {
    register(contentType, KotlinxSerializationJsonJvmConverter(json))
}
