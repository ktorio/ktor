/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.json

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

@Suppress("DEPRECATION_ERROR")
public actual fun defaultSerializer(): JsonSerializer {
    return serializers.maxByOrNull { it::javaClass.name } ?: error(
        """
        Failed to find serializer. Consider adding one of the following dependencies: 
         - ktor-client-gson
         - ktor-client-json
         - ktor-client-serialization
        """.trimIndent()
    )
}

@OptIn(InternalAPI::class)
@Suppress("DEPRECATION_ERROR")
private val serializers = loadServices<JsonSerializer>()
