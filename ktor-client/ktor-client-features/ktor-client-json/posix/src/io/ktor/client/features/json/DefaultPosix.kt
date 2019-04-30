/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.util.*

/**
 * Platform default serializer.
 */

actual fun defaultSerializer(): JsonSerializer = serializers.first()

@InternalAPI
@Suppress("KDocMissingDocumentation")
val serializers: MutableList<JsonSerializer> by lazy {
    mutableListOf<JsonSerializer>()
}
