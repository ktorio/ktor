/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.json.*

class KotlinxJsonWebsocketTest : JsonWebsocketsTest(
    KotlinxWebsocketSerializationConverter(
        Json {
            ignoreUnknownKeys = true
        }
    )
)
