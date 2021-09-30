/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.json.serializer.*
import io.ktor.util.*

@InternalAPI
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
@Suppress("unused")
public val initializer: SerializerInitializer = SerializerInitializer

@InternalAPI
public object SerializerInitializer {
    init {
        @Suppress("DEPRECATION")
        serializersStore += KotlinxSerializer()
    }
}
