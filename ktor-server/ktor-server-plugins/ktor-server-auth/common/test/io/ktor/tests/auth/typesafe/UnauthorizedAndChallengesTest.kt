/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnauthorizedAndChallengesTest {

    @Test
    fun `default challenge sends WWW-Authenticate header`() = testApplication {
        val scheme = basic<TestUser>("challenge-test") {
            realm = "test-realm"
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/profile") { call.respondText(principal.name) }
            }
        }

        val response = client.get("/profile")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate] ?: ""
        assertTrue(wwwAuth.contains("Basic"), "Expected WWW-Authenticate: Basic")
        assertTrue(wwwAuth.contains("test-realm"), "Expected realm in header")
    }

    @Test
    fun `scheme-level onUnauthorized overrides default challenge`() = testApplication {
        val scheme = basic<TestUser>("custom-401") {
            onUnauthorized = { call, _ ->
                call.respondText("Custom 401", status = HttpStatusCode.Unauthorized)
            }
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/profile") { call.respondText(principal.name) }
            }
        }

        val response = client.get("/profile")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Custom 401", response.bodyAsText())
    }

    @Test
    fun `route-level onUnauthorized overrides scheme-level`() = testApplication {
        val scheme = basic<TestUser>("override-test") {
            onUnauthorized = { call, _ ->
                call.respondText("Scheme default", status = HttpStatusCode.Unauthorized)
            }
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/default") { call.respondText(principal.name) }
            }
            authenticateWith(scheme, onUnauthorized = { call, _ ->
                call.respondText("Route override", status = HttpStatusCode.Unauthorized)
            }) {
                get("/custom") { call.respondText(principal.name) }
            }
        }

        assertEquals("Scheme default", client.get("/default").bodyAsText())
        assertEquals("Route override", client.get("/custom").bodyAsText())
    }

    @Test
    fun `onUnauthorized receives correct failure cause`() = testApplication {
        val scheme = testBasicScheme("cause-test")

        routing {
            authenticateWith(
                scheme,
                onUnauthorized = { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/test") { call.respondText(principal.name) }
            }
        }

        // No credentials → NoCredentials
        assertEquals("NoCredentials", client.get("/test").bodyAsText())

        // Invalid credentials → InvalidCredentials
        val invalid = client.get("/test") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals("InvalidCredentials", invalid.bodyAsText())
    }

    @Test
    fun `authenticateWithAnyOf calls multi onUnauthorized with per-scheme failures`() = testApplication {
        val basicScheme = testBasicScheme("anyof-basic")
        val bearerScheme = testBearerScheme("anyof-bearer")

        routing {
            authenticateWithAnyOf(
                basicScheme,
                bearerScheme,
                onUnauthorized = { call, failures ->
                    val text = failures.entries
                        .sortedBy { it.key }
                        .joinToString(";") { (name, cause) -> "$name=${cause::class.simpleName}" }
                    call.respondText(text, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/data") { call.respondText(principal.email) }
            }
        }

        val response = client.get("/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("anyof-basic=NoCredentials;anyof-bearer=NoCredentials", response.bodyAsText())
    }
}
