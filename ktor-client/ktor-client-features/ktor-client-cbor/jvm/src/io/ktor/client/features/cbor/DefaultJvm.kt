/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor

import java.util.*

public actual fun defaultSerializer(): CborSerializer {
    val serializers = ServiceLoader.load(CborSerializer::class.java)
        .toList()

    if (serializers.isEmpty()) {
        error(
            """Fail to find serializer. Consider to add one of the following dependencies: 
 - ktor-client-cbor-serialization"""
        )
    }

    return serializers.maxByOrNull { it::javaClass.name }!!
}
