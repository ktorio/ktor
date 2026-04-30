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
import kotlin.test.assertIs

class RoleBasedAuthTest {

    private fun roleScheme(name: String = "role-test") = acceptAllBasicScheme(name).withRoles { principal ->
        when (principal.name) {
            "admin" -> setOf(TestRole.Admin, TestRole.User)
            "mod" -> setOf(TestRole.Moderator, TestRole.User)
            "user" -> setOf(TestRole.User)
            else -> emptySet()
        }
    }

    @Test
    fun `role-based auth grants forbids and rejects`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = setOf(TestRole.Admin)) {
                assertIs<RoleBasedContext<TestUser, TestRole>>(authenticatedContext())

                get("/admin") {
                    call.respondText("${principal.name}:${roles.joinToString(",") { it.name }}")
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
    fun `custom onForbidden handler receives required roles`() = testApplication {
        val scheme = acceptAllBasicScheme("forbidden-test").withRoles(
            onForbidden = { call, requiredRoles ->
                call.respondText(
                    "Need: ${requiredRoles.joinToString { it.name }}",
                    status = HttpStatusCode.Forbidden
                )
            }
        ) { principal ->
            when (principal.name) {
                "admin" -> setOf(TestRole.Admin, TestRole.User)
                else -> setOf(TestRole.User)
            }
        }

        routing {
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/admin") { call.respondText("ok") }
            }
        }

        val response = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("Need: Admin", response.bodyAsText())
    }

    @Test
    fun `route-level onForbidden overrides scheme-level`() = testApplication {
        val scheme = acceptAllBasicScheme("forbidden-override").withRoles(
            onForbidden = { call, _ ->
                call.respondText("Scheme forbidden", status = HttpStatusCode.Forbidden)
            }
        ) { principal ->
            when (principal.name) {
                "admin" -> setOf(TestRole.Admin, TestRole.User)
                else -> setOf(TestRole.User)
            }
        }

        routing {
            authenticateWith(scheme, roles = setOf(TestRole.Admin)) {
                get("/default") { call.respondText("ok") }
            }
            authenticateWith(
                scheme,
                roles = setOf(TestRole.Admin),
                onForbidden = { call, requiredRoles ->
                    call.respondText(
                        "Route: need ${requiredRoles.joinToString { it.name }}",
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
        assertEquals("Route: need Admin", customResp.bodyAsText())
    }

    @Test
    fun `route-level onUnauthorized overrides scheme-level for role-based auth`() = testApplication {
        val baseScheme = basic<TestUser>("role-unauthorized-override") {
            onUnauthorized = { call, _ ->
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
                onUnauthorized = { call, cause ->
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
}
