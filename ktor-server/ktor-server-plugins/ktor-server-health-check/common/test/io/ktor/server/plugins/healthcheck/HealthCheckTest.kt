/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.healthcheck

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class HealthCheckTest {

    @Test
    fun `liveness endpoint returns UP when all checks pass`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("alive") { true }
            }
        }

        client.get("/health").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"status\":\"UP\"")
            assertContains(body, "\"name\":\"alive\"")
            assertContains(body, "\"status\":\"UP\"")
        }
    }

    @Test
    fun `readiness endpoint returns DOWN when a check fails`() = testApplication {
        install(HealthCheck) {
            readiness("/ready") {
                check("ok") { true }
                check("failing") { false }
            }
        }

        client.get("/ready").let { response ->
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"status\":\"DOWN\"")
        }
    }

    @Test
    fun `check that throws exception reports DOWN with error message`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("broken") { error("connection refused") }
            }
        }

        client.get("/health").let { response ->
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"status\":\"DOWN\"")
            assertContains(body, "\"error\":\"connection refused\"")
        }
    }

    @Test
    fun `endpoint with no checks returns UP`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {}
        }

        client.get("/health").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"status\":\"UP\"")
            assertContains(body, "\"checks\":[]")
        }
    }

    @Test
    fun `multiple endpoints work independently`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("alive") { true }
            }
            readiness("/ready") {
                check("database") { false }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
    }

    @Test
    fun `non-health-check paths are not intercepted`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("ok") { true }
            }
        }

        routing {
            get("/other") { call.respondText("hello") }
        }

        val response = client.get("/other")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun `only GET requests are handled`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("ok") { true }
            }
        }

        val getResponse = client.get("/health")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertContains(getResponse.bodyAsText(), "\"status\":\"UP\"")

        val postResponse = client.post("/health")
        assertFalse(postResponse.bodyAsText().contains("\"status\":\"UP\""))
    }

    @Test
    fun `response content type is application json`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("ok") { true }
            }
        }

        val response = client.get("/health")
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `all checks reported individually in response`() = testApplication {
        install(HealthCheck) {
            readiness("/ready") {
                check("db") { true }
                check("cache") { true }
                check("queue") { true }
            }
        }

        client.get("/ready").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"name\":\"db\"")
            assertContains(body, "\"name\":\"cache\"")
            assertContains(body, "\"name\":\"queue\"")
        }
    }

    @Test
    fun `error message with special characters is escaped in JSON`() = testApplication {
        install(HealthCheck) {
            liveness("/health") {
                check("broken") { error("value with \"quotes\" and\nnewline") }
            }
        }

        client.get("/health").let { response ->
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\\\"quotes\\\"")
            assertContains(body, "\\n")
        }
    }

    @Test
    fun `path without leading slash is normalized`() = testApplication {
        install(HealthCheck) {
            liveness("health") {
                check("ok") { true }
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\":\"UP\"")
    }

    @Test
    fun `mixed healthy and unhealthy checks result in DOWN`() = testApplication {
        install(HealthCheck) {
            readiness("/ready") {
                check("healthy") { true }
                check("unhealthy") { false }
                check("error") { throw RuntimeException("timeout") }
            }
        }

        client.get("/ready").let { response ->
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"status\":\"DOWN\"")
            assertContains(body, "\"name\":\"healthy\"")
            assertContains(body, "\"name\":\"unhealthy\"")
            assertContains(body, "\"error\":\"timeout\"")
        }
    }
}
