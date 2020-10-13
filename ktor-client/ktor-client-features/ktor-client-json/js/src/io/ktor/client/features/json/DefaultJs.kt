/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.util.*

/**
 * Platform default serializer.
 */
public actual fun defaultSerializer(): JsonSerializer =
    serializersStore.first()

@Suppress("KDocMissingDocumentation")
@InternalAPI
public val serializersStore: MutableList<JsonSerializer> = mutableListOf<JsonSerializer>()
