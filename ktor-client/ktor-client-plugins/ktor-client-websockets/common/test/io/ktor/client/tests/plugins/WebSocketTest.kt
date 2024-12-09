/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

class WebSocketTest {

    @Test
    fun testWSSDefaultPort() = testSuspend {
        var port = 0
        val client = HttpClient(MockEngine) {
            install(WebSockets)
            engine {
                addHandler { request ->
                    port = request.url.port

                    respondBadRequest()
                }
            }
        }

        try {
            client.wss(
                method = HttpMethod.Get,
                host = "rueckgr.at",
                path = "/websocket"
            ) {
            }
        } catch (_: Throwable) {
        }

        assertEquals(443, port)
    }
}
