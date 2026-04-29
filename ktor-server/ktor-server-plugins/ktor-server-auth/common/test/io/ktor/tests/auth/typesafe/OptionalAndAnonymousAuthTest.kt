/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

interface AnonTestIdentity
data class AuthenticatedUser(val id: String) : AnonTestIdentity
data class GuestUser(val label: String = "guest") : AnonTestIdentity

class OptionalAndAnonymousAuthTest {

    private val baseScheme = testBasicScheme("optional-test")

    @Test
    fun `optional auth returns principal or null`() = testApplication {
        routing {
            authenticateWith(baseScheme.optional()) {
                get("/me") { call.respondText(principal?.email ?: "anonymous") }
            }
        }

        val withCreds = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, withCreds.status)
        assertEquals("user@test.com", withCreds.bodyAsText())

        val withoutCreds = client.get("/me")
        assertEquals(HttpStatusCode.OK, withoutCreds.status)
        assertEquals("anonymous", withoutCreds.bodyAsText())

        val invalid = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertTrue(invalid.headers[HttpHeaders.WWWAuthenticate].orEmpty().contains("Basic"))
    }

    @Test
    fun `anonymous fallback provides guest or authenticated principal`() = testApplication {
        val anonScheme = basic<AuthenticatedUser>("anon-test") {
            validate { credentials ->
                if (credentials.name == "user") AuthenticatedUser(credentials.name) else null
            }
        }.optional { GuestUser() }

        routing {
            authenticateWith(anonScheme) {
                get("/test") {
                    when (val p = principal) {
                        is AuthenticatedUser -> call.respondText("auth:${p.id}")
                        is GuestUser -> call.respondText("guest:${p.label}")
                        else -> call.respondText("unknown")
                    }
                }
            }
        }

        val guest = client.get("/test")
        assertEquals(HttpStatusCode.OK, guest.status)
        assertEquals("guest:guest", guest.bodyAsText())

        val authed = client.get("/test") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, authed.status)
        assertEquals("auth:user", authed.bodyAsText())

        val invalid = client.get("/test") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertTrue(invalid.headers[HttpHeaders.WWWAuthenticate].orEmpty().contains("Basic"))
    }
}
