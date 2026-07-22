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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationSchemeWithRolesTest {

    private fun roleScheme(name: String = "role-test") =
        acceptAllBasicScheme(name).withRoles { principal ->
            when (principal.name) {
                "admin" -> setOf(TestRole.Admin, TestRole.User)
                "mod" -> setOf(TestRole.Moderator, TestRole.User)
                "user" -> setOf(TestRole.User)
                else -> emptySet()
            }
        }

    @Test
    fun `role-based auth grants, forbids, and rejects`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = setOf(TestRole.Admin)) {
                get("/admin") {
                    val user = call.principal
                    call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
                }
            }
        }

        // Authorized → 200 with roles
        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals(HttpStatusCode.OK, adminResp.status)
        assertEquals("admin:Admin,User", adminResp.bodyAsText())

        // Authenticated but wrong role → 403
        val userResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)

        // Unauthenticated → 401, not 403
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin").status)
    }

    @Test
    fun `route-level onForbidden overrides scheme-level`() = testApplication {
        val scheme = acceptAllBasicScheme("forbidden-override")
            .withRoles(
                onForbidden = { call.respondText("Scheme forbidden", status = HttpStatusCode.Forbidden) },
                resolveRoles = { principal ->
                    when (principal.name) {
                        "admin" -> setOf(TestRole.Admin, TestRole.User)
                        else -> setOf(TestRole.User)
                    }
                }
            )

        routing {
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/default") { call.respondText("ok") }
            }
            authenticateWith(
                scheme,
                roles = setOf(TestRole.Admin),
                onForbidden = { requiredRoles ->
                    call.respondText(
                        "Route: requires ${requiredRoles.joinToString { it.name }}",
                        status = HttpStatusCode.Forbidden
                    )
                }
            ) {
                get("/custom") { call.respondText("ok") }
            }
        }

        val defaultResp = client.get("/default") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("Scheme forbidden", defaultResp.bodyAsText())

        val customResp = client.get("/custom") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("Route: requires Admin", customResp.bodyAsText())
    }

    @Test
    fun `route-level onUnauthorized overrides scheme-level for role-based auth`() = testApplication {
        val baseScheme = basic<TestUser>("role-unauthorized-override") {
            onUnauthorized = {
                call.respondText("Scheme unauthorized", status = HttpStatusCode.Unauthorized)
            }
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "pass") {
                    TestUser(credentials.name, "admin@test.com")
                } else {
                    null
                }
            }
        }
        val scheme = baseScheme.withRoles { principal ->
            if (principal.name == "admin") setOf(TestRole.Admin) else emptySet()
        }

        routing {
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/default") { call.respondText("ok") }
            }
            authenticateWith(
                scheme,
                roles = setOf(TestRole.Admin),
                onUnauthorized = { cause ->
                    call.respondText(
                        "Route unauthorized:${cause::class.simpleName}",
                        status = HttpStatusCode.Unauthorized
                    )
                }
            ) {
                get("/custom") { call.respondText("ok") }
            }
        }

        assertEquals("Scheme unauthorized", client.get("/default").bodyAsText())
        assertEquals("Route unauthorized:NoCredentials", client.get("/custom").bodyAsText())
    }

    @Test
    fun `role-based auth on sessions grants, forbids, and rejects`() = testApplication {
        @Serializable
        data class UserSession(val name: String)

        val sessionScheme = session<UserSession, TestUser>("session-role-scheme") {
            validate { session ->
                when (session.name) {
                    "admin" -> TestUser("admin", "admin@test.com")
                    "user" -> TestUser("user", "user@test.com")
                    else -> null
                }
            }
        }
        val scheme = sessionScheme.withRoles { principal ->
            when (principal.name) {
                "admin" -> setOf(TestRole.Admin, TestRole.User)
                "user" -> setOf(TestRole.User)
                else -> emptySet()
            }
        }

        routing {
            install(sessionScheme)
            get("/set-session/{name}") {
                sessionScheme.setSession(UserSession(call.requirePathParameter("name")))
                call.respondText("ok")
            }
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/admin") {
                    val user = call.principal
                    call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
                }
            }
        }

        // Mock session authentication
        fun clientWithSession() = createClient { install(HttpCookies) }

        // Valid session with correct role → 200 with roles
        val adminClient = clientWithSession()
        adminClient.get("/set-session/admin")
        val adminResp = adminClient.get("/admin")
        assertEquals(HttpStatusCode.OK, adminResp.status)
        assertEquals("admin:Admin,User", adminResp.bodyAsText())

        // Valid session but wrong role → 403
        val userClient = clientWithSession()
        userClient.get("/set-session/user")
        val userResp = userClient.get("/admin")
        assertEquals(HttpStatusCode.Forbidden, userResp.status)

        // Invalid session → 401
        val invalidClient = clientWithSession()
        val invalidResp = invalidClient.get("/admin")
        assertEquals(HttpStatusCode.Unauthorized, invalidResp.status)

        // No session → 401
        val noSessionResp = client.get("/admin")
        assertEquals(HttpStatusCode.Unauthorized, noSessionResp.status)
    }

    @Test
    fun `optional role-based auth allows anonymous, forbids wrong role, and grants correct role`() = testApplication {
        routing {
            authenticateWithOptional(roleScheme(), roles = setOf(TestRole.Admin)) {
                get("/admin") {
                    val user = call.principalOrNull
                    if (user == null) {
                        call.respondText("anonymous")
                    } else {
                        call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
                    }
                }
            }
        }

        val anonymousResp = client.get("/admin")
        assertEquals(HttpStatusCode.OK, anonymousResp.status)
        assertEquals("anonymous", anonymousResp.bodyAsText())

        val forbiddenResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenResp.status)

        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals(HttpStatusCode.OK, adminResp.status)
        assertEquals("admin:Admin,User", adminResp.bodyAsText())
    }

    @Test
    fun `onForbidden that does not complete the call allows route handler to run`() = testApplication {
        val scheme = acceptAllBasicScheme("incomplete-forbidden")
            .withRoles(
                onForbidden = { /* intentionally does not respond */ },
                resolveRoles = { principal ->
                    when (principal.name) {
                        "admin" -> setOf(TestRole.Admin)
                        else -> setOf(TestRole.User)
                    }
                }
            )

        routing {
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/admin") {
                    call.respondText("route reached")
                }
            }
        }

        val response = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("route reached", response.bodyAsText())
    }

    @Test
    fun `roles null skips forbidden but resolves roles`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = null) {
                get("/any-authenticated") {
                    val user = call.principal
                    call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
                }
            }
        }

        val userResp = client.get("/any-authenticated") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, userResp.status)
        assertEquals("user:User", userResp.bodyAsText())
    }
}
