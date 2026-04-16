/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.serialization.jackson3.JacksonWebsocketContentConverter

class JacksonWebsocketTest : JsonWebsocketsTest(JacksonWebsocketContentConverter())
