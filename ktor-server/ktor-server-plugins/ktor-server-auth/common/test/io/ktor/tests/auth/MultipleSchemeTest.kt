/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class MultipleSchemeTest {

    interface AppUser {
        val email: String
    }

    data class BasicUser(override val email: String) : AppUser
    data class BearerUser(override val email: String) : AppUser

    private val basicScheme = basic<BasicUser>("multi-basic") {
        validate { BasicUser("${it.name}@basic.com") }
    }

    private val bearerScheme = bearer<BearerUser>("multi-bearer") {
        validate { BearerUser("${it.token}@bearer.com") }
    }

    @Test
    fun `anyOf accepts matching schemes and rejects when none match`() = testApplication {
        routing {
            authenticateWithAnyOf(basicScheme, bearerScheme) {
                get("/profile") {
                    // call.principal should be supertype of BasicUser and BearerUser
                    call.respondText(call.principal.email)
                }
            }
        }

        // Basic credentials → 200
        val basicRes = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals(HttpStatusCode.OK, basicRes.status)
        assertEquals("alice@basic.com", basicRes.bodyAsText())

        // Bearer credentials → 200
        val bearerRes = client.get("/profile") {
            header(HttpHeaders.Authorization, bearerAuthHeader("tok"))
        }
        assertEquals(HttpStatusCode.OK, bearerRes.status)
        assertEquals("tok@bearer.com", bearerRes.bodyAsText())

        // No credentials → 401
        assertEquals(HttpStatusCode.Unauthorized, client.get("/profile").status)
    }

    @Test
    fun `anyOf with custom onUnauthorized handler`() = testApplication {
        val adminScheme = basic<TestUser>("admin-basic-scheme") {
            validate {
                if (it.name == "superadmin") TestUser("superadmin", "superadmin@admin.com") else null
            }
        }
        val userScheme = basic<TestUser>("user-basic-scheme") {
            validate {
                if (it.name == "ordinaryuser") TestUser("ordinaryuser", "ordinaryuser@user.com") else null
            }
        }

        routing {
            authenticateWithAnyOf(
                adminScheme,
                userScheme,
                onUnauthorized = { call.respondText("Unauthorized") }
            ) {
                get("/any") {
                    // call.session is not available here
                    call.respondText("Any Scheme: ${call.principal.name}")
                }
            }
        }

        val unauthorizedResponse = client.get("/any")
        assertEquals(HttpStatusCode.OK, unauthorizedResponse.status)
        assertEquals("Unauthorized", unauthorizedResponse.bodyAsText())

        val successResponse = client.get("/any") {
            header(HttpHeaders.Authorization, basicAuthHeader("ordinaryuser"))
        }
        assertEquals(HttpStatusCode.OK, successResponse.status)
        assertEquals("Any Scheme: ordinaryuser", successResponse.bodyAsText())
    }

    @Test
    fun `anyOf uses first scheme onUnauthorized when route handler is absent`() = testApplication {
        val firstScheme = basic<TestUser>("first-with-handler") {
            onUnauthorized = { call.respondText("First scheme unauthorized") }
            validate { TestUser(it.name, "${it.name}@first.com") }
        }
        val secondScheme = basic<TestUser>("second-no-handler") {
            validate { TestUser(it.name, "${it.name}@second.com") }
        }

        routing {
            authenticateWithAnyOf(firstScheme, secondScheme) {
                get("/any") {
                    call.respondText(call.principal.email)
                }
            }
        }

        val response = client.get("/any")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("First scheme unauthorized", response.bodyAsText())
    }

    @Test
    fun `anyOf accepts session or basic authentication`() = testApplication {
        val basicAuth = basic<TestUser>("anyof-mixed-basic") {
            validate { TestUser(it.name, "${it.name}@basic.com") }
        }
        val sessionAuth = session<UserSession, TestUser>("anyof-mixed-session") {
            validate { session -> TestUser(session.username, "${session.username}@session.com") }
        }

        routing {
            install(sessionAuth)
            get("/set-session/{username}") {
                sessionAuth.setSession(UserSession(call.parameters["username"]!!))
                call.respondText("ok")
            }
            authenticateWithAnyOf(basicAuth, sessionAuth) {
                get("/profile") {
                    call.respondText(call.principal.email)
                }
            }
        }

        val basicResponse = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals(HttpStatusCode.OK, basicResponse.status)
        assertEquals("alice@basic.com", basicResponse.bodyAsText())

        val sessionClient = createClient { install(HttpCookies) }
        sessionClient.get("/set-session/bob")
        val sessionResponse = sessionClient.get("/profile")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        assertEquals("bob@session.com", sessionResponse.bodyAsText())
    }
}
