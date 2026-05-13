/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class JettyJacksonIOExhaustionTest :
    EngineTestBase<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {

    init {
        enableSsl = false
        enableHttp2 = false
    }

    override fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        super.plugins(application, routingConfig)
        application.install(ContentNegotiation) {
            jackson {}
        }
    }

    @Test
    fun `concurrent jackson deserialization exhausts Dispatchers IO`() = runTest {
        createAndStartServer {
            post("/json/jackson") {
                val body = call.receive<Map<String, Any>>()
                call.respond(body)
            }
        }

        val concurrentRequests = 50

        HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                jackson {}
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
            }
        }.use { client ->
            withTimeout(60.seconds) {
                coroutineScope {
                    val results = (1..concurrentRequests).map { i ->
                        async(Dispatchers.Default) {
                            client.post("http://127.0.0.1:$port/json/jackson") {
                                contentType(ContentType.Application.Json)
                                setBody(mapOf("request" to i, "data" to "value$i"))
                            }
                        }
                    }.awaitAll()

                    results.forEach { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                    }
                }
            }
        }
    }
}
