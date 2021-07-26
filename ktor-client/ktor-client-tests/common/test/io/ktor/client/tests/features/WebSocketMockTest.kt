/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.util.collections.*
import kotlinx.atomicfu.*
import kotlin.test.*

class WebSocketMockTest {
    val header = atomic<String?>("")

    @Test
    fun testNoEmptyExtensionHeader() = testSuspend {
        if (PlatformUtils.IS_BROWSER || PlatformUtils.IS_NODE) return@testSuspend

        val client = HttpClient(MockEngine) {
            install(WebSockets)

            engine {
                addHandler { request ->
                    header.value = request.headers[HttpHeaders.SecWebSocketExtensions]
                    respondBadRequest()
                }
            }
        }

        assertFailsWith<ClientRequestException> {
            client.ws("127.0.0.1") {
            }
        }

        assertNull(header.value)
    }
}
