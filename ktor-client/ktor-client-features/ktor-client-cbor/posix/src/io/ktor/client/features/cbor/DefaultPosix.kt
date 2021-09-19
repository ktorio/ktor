/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor

import io.ktor.util.*

/**
 * Platform default serializer.
 */
public actual fun defaultSerializer(): CborSerializer = serializers.first()

@InternalAPI
@Suppress("KDocMissingDocumentation")
public val serializers: MutableList<CborSerializer> by lazy {
    mutableListOf<CborSerializer>()
}
