/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.*
import kotlin.test.*

class AndroidProxyTest : TestWithKtor() {
    private val factory: HttpClientEngineFactory<*> = Android

    override val server = embeddedServer(Jetty, serverPort) {
        routing {
            post("/") {
                assertEquals("Hello, server", call.receive())
                call.respondText("Hello, client")
            }
        }
    }

    @Test
    fun testProxyPost() = testWithEngine(factory) {
        config {
            engine {
                if (this is AndroidEngineConfig) {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", serverPort))
                }
            }
        }
        test { client ->
            val text = client.post("http://somewhere.else") {
                setBody("Hello, server")
            }.body<String>()
            assertEquals("Hello, client", text)
        }
    }
}
