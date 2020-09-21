/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import java.util.*

public actual fun defaultSerializer(): JsonSerializer {
    val serializers = ServiceLoader.load(JsonSerializer::class.java)
        .toList()

    if (serializers.isEmpty()) error(
        "Fail to find serializer. Consider to add one of the following dependencies: \n" +
            " - ktor-client-gson\n" +
            " - ktor-client-json\n" +
            " - ktor-client-serialization"
    )

    return serializers.maxByOrNull { it::javaClass.name }!!
}
