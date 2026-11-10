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
import kotlin.test.assertFailsWith

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
    fun `test role-based authentication flow`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = allOf(TestRole.Admin)) {
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
            authenticateWith(scheme, roles = allOf(TestRole.Admin)) {
                get("/default") { call.respondText("ok") }
            }
            authenticateWith(
                scheme,
                roles = allOf(TestRole.Admin),
                onForbidden = {
                    val rolesStr = call.principal.roles.joinToString(separator = ",")
                    val message = "Route: requires Admin, but got $rolesStr"
                    call.respondText(message, status = HttpStatusCode.Forbidden)
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
        assertEquals("Route: requires Admin, but got User", customResp.bodyAsText())
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
            authenticateWith(scheme, roles = allOf(TestRole.Admin)) {
                get("/default") { call.respondText("ok") }
            }
            authenticateWith(
                scheme,
                roles = allOf(TestRole.Admin),
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
    fun `test role-based authentication with session flow`() = testApplication {
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
            authenticateWith(scheme, roles = allOf(TestRole.Admin)) {
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
    fun `test optional role-based authentication flow`() = testApplication {
        routing {
            authenticateWithOptional(roleScheme(), roles = allOf(TestRole.Admin)) {
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
    fun `onForbidden responds 403 when call is not handled`() = testApplication {
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
            authenticateWith(scheme, roles = allOf(TestRole.Admin)) {
                get("/admin") {
                    call.respondText("route reached")
                }
            }
        }

        val response = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
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

    @Test
    fun `anyOf allows principal with at least one required role`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = anyOf(TestRole.Admin, TestRole.Moderator)) {
                get("/moderation") { call.respondText(call.principal.name) }
            }
        }

        for (name in listOf("admin", "mod")) {
            val response = client.get("/moderation") {
                header(HttpHeaders.Authorization, basicAuthHeader(name))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(name, response.bodyAsText())
        }

        val userResp = client.get("/moderation") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)
    }

    @Test
    fun `empty anyOf is rejected when route is built`() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                routing {
                    authenticateWith(roleScheme(), roles = anyOf()) {
                        get("/never") { call.respondText("unreachable") }
                    }
                }
                startApplication()
            }
        }
    }

    @Test
    fun `optional route enforces anyOf for authenticated callers`() = testApplication {
        routing {
            authenticateWithOptional(roleScheme(), roles = anyOf(TestRole.Admin, TestRole.Moderator)) {
                get("/optional") { call.respondText(call.principalOrNull?.name ?: "anonymous") }
            }
        }

        assertEquals("anonymous", client.get("/optional").bodyAsText())
        val modResp = client.get("/optional") {
            header(HttpHeaders.Authorization, basicAuthHeader("mod"))
        }
        assertEquals("mod", modResp.bodyAsText())
        val userResp = client.get("/optional") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)
    }

    @Test
    fun `requiredRoles is available in forbidden handler and route`() = testApplication {
        val scheme = roleScheme()
        routing {
            authenticateWith(
                scheme,
                roles = anyOf(TestRole.Admin, TestRole.Moderator),
                onForbidden = {
                    val missing = call.requiredRoles.roles.joinToString(",") { it.name }
                    call.respondText("requires one of $missing", status = HttpStatusCode.Forbidden)
                }
            ) {
                get("/admin") { call.respondText(call.requiredRoles.roles.joinToString(",") { it.name }) }
            }
            authenticateWith(scheme, roles = null) {
                get("/open") { call.respondText(call.requiredRoles.roles.isEmpty().toString()) }
            }
        }

        val userResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)
        assertEquals("requires one of Admin,Moderator", userResp.bodyAsText())

        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals("Admin,Moderator", adminResp.bodyAsText())

        val openResp = client.get("/open") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("true", openResp.bodyAsText())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated set overload requires every role`() = testApplication {
        routing {
            authenticateWith(roleScheme(), roles = setOf(TestRole.Moderator, TestRole.User)) {
                get("/both") { call.respondText("ok") }
            }
            authenticateWithOptional(roleScheme("optional-set"), roles = setOf(TestRole.Admin)) {
                get("/optional") { call.respondText("ok") }
            }
        }

        val modResp = client.get("/both") {
            header(HttpHeaders.Authorization, basicAuthHeader("mod"))
        }
        assertEquals(HttpStatusCode.OK, modResp.status)
        val adminResp = client.get("/both") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals(HttpStatusCode.Forbidden, adminResp.status)
        val userResp = client.get("/optional") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)
    }
}
