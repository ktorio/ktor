/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.json.serializer

import io.ktor.client.plugins.json.*
import io.ktor.util.*

@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused", "DEPRECATION")
@EagerInitialization
private val initHook = SerializerInitializer

@Suppress("DEPRECATION")
@OptIn(InternalAPI::class)
private object SerializerInitializer {
    init {
        serializers.add(KotlinxSerializer())
    }
}
