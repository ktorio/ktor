/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.util.*

@InternalAPI
@Suppress("unused")
public val initializer: SerializerInitializer = SerializerInitializer

@InternalAPI
public object SerializerInitializer {
    init {
        serializersStore += KotlinxSerializer()
    }
}
