/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.serializer

import io.ktor.client.features.json.*

private val InitHook = SerializerInitializer

private object SerializerInitializer {
    init {
        serializers.add(KotlinxSerializer())
    }
}
