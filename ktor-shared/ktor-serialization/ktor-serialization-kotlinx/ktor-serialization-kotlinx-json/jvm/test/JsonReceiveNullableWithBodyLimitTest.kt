/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonReceiveNullableWithBodyLimitTest {

    @Serializable
    data class Payload(val value: String)

    @Test
    fun `receive nullable with empty body returns null`() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, KotlinxSerializationConverter(DefaultJson))
        }

        var receivedWasNull = false
        routing {
            post("/") {
                val received: Payload? = call.receiveNullable()
                receivedWasNull = received == null
                call.respond(HttpStatusCode.OK, "ok")
            }
        }

        val response = client.post("/") {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
        assertTrue(receivedWasNull, "receiveNullable<Payload?>() should return null for an empty body")
    }

    @Test
    fun `receive nullable with limit and empty body returns null`() = testApplication {
        install(RequestBodyLimit) {
            bodyLimit { 1024 }
        }
        install(ContentNegotiation) {
            register(ContentType.Application.Json, KotlinxSerializationConverter(DefaultJson))
        }

        var receivedWasNull: Boolean
        routing {
            post("/") {
                val received: Payload? = call.receiveNullable()
                receivedWasNull = received == null
                call.respond(HttpStatusCode.OK, "ok")
            }
        }

        // Repeat to stress any residual scheduling sensitivity caused by RequestBodyLimit
        // wrapping the incoming channel via GlobalScope.writer.
        repeat(50) {
            receivedWasNull = false
            val response = client.post("/") {
                contentType(ContentType.Application.Json)
            }
            assertEquals(HttpStatusCode.OK, response.status, "iteration #$it returned ${response.status}")
            assertEquals("ok", response.bodyAsText(), "iteration #$it body mismatch")
            assertTrue(receivedWasNull, "iteration #$it: receiveNullable<Payload?>() should return null")
        }
    }
}
