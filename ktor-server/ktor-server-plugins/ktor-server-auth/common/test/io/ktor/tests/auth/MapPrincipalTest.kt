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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

data class MappedUser(val label: String)

class MapPrincipalTest {

    @Test
    fun `mapped simple scheme exposes mapped principal`() = testApplication {
        val mapped = basic<TestUser>("mapped-basic") {
            realm = "test"
            validate { credentials ->
                if (credentials.password != "pass") return@validate null
                when (credentials.name) {
                    "user", "blocked" -> TestUser(credentials.name, "${credentials.name}@test.com")
                    else -> null
                }
            }
        }.mapPrincipal { user ->
            user.takeIf { it.name != "blocked" }?.let { MappedUser(it.email) }
        }

        routing {
            authenticateWith(mapped) {
                get("/me") { call.respondText(call.principal.label) }
            }
        }

        val ok = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("user@test.com", ok.bodyAsText())

        val rejected = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("blocked"))
        }
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)

        val invalid = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `mapped session scheme keeps session helpers and transport`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("mapped-session") {
            validate { session -> TestUser(session.username, "${session.username}@test.com") }
        }
        val mapped = sessionScheme.mapPrincipal { user -> MappedUser(user.name) }

        routing {
            install(mapped)
            get("/set-session") {
                mapped.setSession(UserSession("Alice", visits = 2))
                call.respondText("ok")
            }
            authenticateWith(mapped) {
                get("/protected") {
                    call.respondText("${call.session.username}:${call.session.visits}:${call.principal.label}")
                }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/protected").status)

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")
        val response = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice:2:Alice", response.bodyAsText())
    }

    @Test
    fun `mapped session scheme retains csrf preinstallation`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("mapped-csrf-session") {
            csrfProtection {
                allowOrigin("https://localhost:8080")
            }
            validate { session -> TestUser(session.username, "${session.username}@test.com") }
        }
        val mapped = sessionScheme.mapPrincipal { user -> MappedUser(user.name) }

        routing {
            install(mapped)
            get("/set-session") {
                mapped.setSession(UserSession("Alice"))
                call.respondText("ok")
            }
            authenticateWith(mapped) {
                post("/protected") {
                    call.respondText("${call.session.username}:${call.principal.label}")
                }
            }
        }

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")

        val missingOrigin = cookieClient.post("/protected")
        assertEquals(HttpStatusCode.BadRequest, missingOrigin.status)
        assertContains(missingOrigin.bodyAsText(), "Cross-site request validation failed")

        val allowed = cookieClient.post("/protected") {
            header(HttpHeaders.Origin, "https://localhost:8080")
        }
        assertEquals(HttpStatusCode.OK, allowed.status)
        assertEquals("Alice:Alice", allowed.bodyAsText())
    }

    @Test
    fun `mapped session scheme still requires Sessions before typed route`() = testApplication {
        val mapped = session<UserSession, UserSession>("mapped-missing-session") {
            validate { session -> session }
        }.mapPrincipal { session -> session }

        routing {
            authenticateWith(mapped) {}
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `original and mapped schemes sharing a provider protect different routes`() = testApplication {
        val original = testBasicScheme("shared-provider")
        val mapped = original.mapPrincipal { user -> MappedUser(user.email) }

        routing {
            authenticateWith(original) {
                get("/original") { call.respondText(call.principal.email) }
            }
            authenticateWith(mapped) {
                get("/mapped") { call.respondText(call.principal.label) }
            }
        }

        val originalResponse = client.get("/original") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("user@test.com", originalResponse.bodyAsText())

        val mappedResponse = client.get("/mapped") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("user@test.com", mappedResponse.bodyAsText())
    }

    @Test
    fun `mapPrincipal transform exceptions propagate`() = testApplication {
        val mapped = testBasicScheme("throwing-map").mapPrincipal { _: TestUser ->
            error("mapping failed")
        }

        routing {
            authenticateWith(mapped) {
                get("/me") { error("route should not run") }
            }
        }

        val response = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `mapped session scheme with roles grants forbids and rejects`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("mapped-session-roles") {
            validate { session ->
                when (session.username) {
                    "admin" -> TestUser("admin", "admin@test.com")
                    "user" -> TestUser("user", "user@test.com")
                    else -> null
                }
            }
        }
        val scheme = sessionScheme
            .mapPrincipal { user -> MappedUser(user.name) }
            .withRoles { principal ->
                when (principal.label) {
                    "admin" -> setOf(TestRole.Admin, TestRole.User)
                    "user" -> setOf(TestRole.User)
                    else -> emptySet()
                }
            }

        routing {
            install(scheme.base)
            get("/set-session/{name}") {
                scheme.base.setSession(UserSession(call.parameters["name"]!!))
                call.respondText("ok")
            }
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/admin") {
                    val user = call.principal
                    call.respondText(
                        "${call.session.username}:${user.label}:${user.roles.joinToString(",") { it.name }}"
                    )
                }
            }
        }

        fun clientWithSession() = createClient { install(HttpCookies) }

        val adminClient = clientWithSession()
        adminClient.get("/set-session/admin")
        val adminResp = adminClient.get("/admin")
        assertEquals(HttpStatusCode.OK, adminResp.status)
        assertEquals("admin:admin:Admin,User", adminResp.bodyAsText())

        val userClient = clientWithSession()
        userClient.get("/set-session/user")
        assertEquals(HttpStatusCode.Forbidden, userClient.get("/admin").status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin").status)
    }

    @Test
    fun `optional authentication allows missing credentials but rejects null mapping`() = testApplication {
        val mapped = basic<TestUser>("optional-map") {
            validate { credentials ->
                if (credentials.password != "pass") return@validate null
                when (credentials.name) {
                    "user", "admin" -> TestUser(credentials.name, "${credentials.name}@test.com")
                    else -> null
                }
            }
        }.mapPrincipal { user ->
            user.takeIf { it.name != "user" }?.let { MappedUser(it.email) }
        }

        routing {
            authenticateWithOptional(mapped) {
                get("/me") { call.respondText(call.principalOrNull?.label ?: "anonymous") }
            }
        }

        val missing = client.get("/me")
        assertEquals(HttpStatusCode.OK, missing.status)
        assertEquals("anonymous", missing.bodyAsText())

        val ok = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("admin@test.com", ok.bodyAsText())

        val rejected = client.get("/me") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
    }

    @Test
    fun `unauthorized handlers receive invalid credentials for null mapping`() = testApplication {
        val mapped = basic<TestUser>("unauth-map") {
            onUnauthorized = { cause ->
                call.respondText("scheme:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
            }
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }.mapPrincipal { null }

        routing {
            authenticateWith(mapped) {
                get("/scheme") { error("route should not run") }
            }
            authenticateWith(mapped, onUnauthorized = { cause ->
                call.respondText("route:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
            }) {
                get("/route") { error("route should not run") }
            }
        }

        val schemeResponse = client.get("/scheme") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Unauthorized, schemeResponse.status)
        assertEquals("scheme:InvalidCredentials", schemeResponse.bodyAsText())

        val routeResponse = client.get("/route") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Unauthorized, routeResponse.status)
        assertEquals("route:InvalidCredentials", routeResponse.bodyAsText())
    }
}
