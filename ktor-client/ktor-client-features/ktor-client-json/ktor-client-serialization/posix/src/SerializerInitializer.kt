/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.json.serializer

import io.ktor.client.features.json.*

@Suppress("unused")
private val InitHook = SerializerInitializer

private object SerializerInitializer {
    init {
        serializers.add(KotlinxSerializer())
    }
}
