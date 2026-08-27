/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.InternalAPI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnauthorizedAndChallengesTest {

    private class FailTwiceProvider(name: String) : AuthenticationProvider(Config(name)) {
        private class Config(name: String) : AuthenticationProvider.Config(name)

        override suspend fun onAuthenticate(context: AuthenticationContext) {
            context.challenge("first", AuthenticationFailedCause.NoCredentials) { _, _ -> }
            context.challenge("last", AuthenticationFailedCause.InvalidCredentials) { _, _ -> }
        }
    }

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
                get("/profile") { call.respondText(call.principal.name) }
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
            onUnauthorized = { _ ->
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
                get("/profile") { call.respondText(call.principal.name) }
            }
        }

        val response = client.get("/profile")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Custom 401", response.bodyAsText())
    }

    @Test
    fun `route-level onUnauthorized overrides scheme-level`() = testApplication {
        val scheme = basic<TestUser>("override-test") {
            onUnauthorized = { _ ->
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
                get("/default") { call.respondText(call.principal.name) }
            }
            authenticateWith(scheme, onUnauthorized = { _ ->
                call.respondText("Route override", status = HttpStatusCode.Unauthorized)
            }) {
                get("/custom") { call.respondText(call.principal.name) }
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
                onUnauthorized = { cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/test") { call.respondText(call.principal.name) }
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
    fun `onUnauthorized that does not respond falls back to default challenge`() = testApplication {
        var handlerRan = false
        var routeHandlerRan = false
        val scheme = basic<TestUser>("silent-401") {
            realm = "silent-realm"
            validate { null }
        }

        routing {
            authenticateWith(scheme, onUnauthorized = { _ -> handlerRan = true }) {
                get("/protected") {
                    routeHandlerRan = true
                    call.respondText("secret")
                }
            }
        }

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(handlerRan, "custom onUnauthorized must still run")
        assertFalse(routeHandlerRan, "route handler must not run for a failed authentication")
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate] ?: ""
        assertTrue(wwwAuth.contains("silent-realm"), "default challenge must run as fallback")
    }

    @Test
    fun `onUnauthorized that only sets status does not open the route handler`() = testApplication {
        var routeHandlerRan = false
        val scheme = basic<TestUser>("status-only-401") {
            onUnauthorized = { _ ->
                // Sets the status line but does not commit a response.
                call.response.status(HttpStatusCode.Forbidden)
            }
            validate { null }
        }

        routing {
            authenticateWith(scheme) {
                get("/protected") {
                    routeHandlerRan = true
                    call.respondText("secret")
                }
            }
        }

        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(routeHandlerRan, "route handler must not run for a failed authentication")
    }

    @Test
    fun `anyOf onUnauthorized that does not respond falls back to challenges`() = testApplication {
        var routeHandlerRan = false
        val basicScheme = testBasicScheme("silent-anyof-basic")
        val bearerScheme = testBearerScheme("silent-anyof-bearer")

        routing {
            authenticateWithAnyOf(basicScheme, bearerScheme, onUnauthorized = { _ -> }) {
                get("/data") {
                    routeHandlerRan = true
                    call.respondText("secret")
                }
            }
        }

        val response = client.get("/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(routeHandlerRan, "route handler must not run for a failed authentication")
    }

    @Test
    fun `authenticateWithAnyOf calls multi onUnauthorized with per-scheme failures`() = testApplication {
        val basicScheme = testBasicScheme("anyof-basic")
        val bearerScheme = testBearerScheme("anyof-bearer")

        routing {
            authenticateWithAnyOf(
                basicScheme,
                bearerScheme,
                onUnauthorized = { failures ->
                    val text = failures.entries
                        .sortedBy { it.key }
                        .joinToString(";") { (name, cause) -> "$name=${cause::class.simpleName}" }
                    call.respondText(text, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/data") { call.respondText(call.principal.email) }
            }
        }

        val response = client.get("/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("anyof-basic=NoCredentials;anyof-bearer=NoCredentials", response.bodyAsText())
    }

    @Test
    fun `authenticateWithAnyOf reports final failure per scheme`() = testApplication {
        val scheme = AuthenticationScheme.from<TestUser>(
            provider = FailTwiceProvider("final-failure"),
            onUnauthorized = null,
        )

        routing {
            authenticateWithAnyOf(
                scheme,
                onUnauthorized = { failures ->
                    val cause = failures.getValue("final-failure")
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/data") { call.respondText(call.principal.email) }
            }
        }

        val response = client.get("/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("InvalidCredentials", response.bodyAsText())
    }
}
