/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlin.test.Test

class LoggingJsonTest {

    @Test
    fun testLoggingWith3KB() = testWithEngine(MockEngine) {
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(ContentNegotiation) {
                jackson()
            }

            engine {
                addHandler { request ->
                    request.body.toByteArray()
                    respondOk()
                }
            }
        }

        test { client ->
            val payload = "a".repeat(3000)

            val response = client.post("http://localhost/data") {
                contentType(ContentType.Application.Json)
                setBody(Payload(payload))
            }
            response.bodyAsText()
        }
    }

    @Test
    fun testLoggingWith6KB() = testWithEngine(MockEngine) {
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(ContentNegotiation) {
                jackson()
            }

            engine {
                addHandler { request ->
                    request.body.toByteArray()
                    respondOk()
                }
            }
        }

        test { client ->
            val payload = "a".repeat(6000)

            val response = client.post("http://localhost/data") {
                contentType(ContentType.Application.Json)
                setBody(Payload(payload))
            }

            response.bodyAsText()
        }
    }
}

class Payload(val data: String)
