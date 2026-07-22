/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth

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

class NestedRoutesTest {

    private val basicScheme = acceptAllBasicScheme("nested-basic")

    private val roleScheme = acceptAllBasicScheme("nested-role").withRoles { principal ->
        when (principal.name) {
            "admin" -> setOf(TestRole.Admin, TestRole.User)
            else -> setOf(TestRole.User)
        }
    }

    @Test
    fun `nested and deeply nested routes inherit authentication`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                route("/api") {
                    get("/users") { call.respondText("users:${call.principal.name}") }
                    get("/items") { call.respondText("items:${call.principal.name}") }
                    route("/v2") {
                        route("/admin") {
                            get("/deep") { call.respondText("deep:${call.principal.name}") }
                        }
                    }
                }
            }
        }

        val auth = basicAuthHeader("user")

        val usersResp = client.get("/api/users") { header(HttpHeaders.Authorization, auth) }
        assertEquals("users:user", usersResp.bodyAsText())

        val itemsResp = client.get("/api/items") { header(HttpHeaders.Authorization, auth) }
        assertEquals("items:user", itemsResp.bodyAsText())

        val deepResp = client.get("/api/v2/admin/deep") { header(HttpHeaders.Authorization, auth) }
        assertEquals("deep:user", deepResp.bodyAsText())

        // All reject without credentials
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v2/admin/deep").status)
    }

    @Test
    fun `authenticateWith inside authenticateWith requires both layers`() = testApplication {
        data class OuterUser(val name: String)
        data class InnerUser(val name: String)

        val outerScheme = form<OuterUser>("nested-form") {
            validate { credentials ->
                if (credentials.name == "outer" && credentials.password == "pass") {
                    OuterUser(credentials.name)
                } else {
                    null
                }
            }
        }
        val innerScheme = basic<InnerUser>("nested-basic-inner") {
            validate { credentials ->
                if (credentials.name == "inner" && credentials.password == "pass") {
                    InnerUser(credentials.name)
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(outerScheme) {
                authenticateWith(innerScheme) {
                    post("/nested") {
                        val outerPrincipal = call.principal<OuterUser>()
                        call.respondText("${outerPrincipal?.name}:${call.principal.name}")
                    }
                }
            }
        }

        val onlyOuter = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=outer&password=pass")
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyOuter.status)

        val onlyInner = client.post("/nested") {
            header(HttpHeaders.Authorization, basicAuthHeader("inner"))
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyInner.status)

        val both = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            header(HttpHeaders.Authorization, basicAuthHeader("inner"))
            setBody("user=outer&password=pass")
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("outer:inner", both.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/nested").status)
    }

    @Test
    fun `authenticateWith with roles inside authenticateWith`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                authenticateWith(roleScheme, roles = setOf(TestRole.Admin)) {
                    get("/admin") {
                        call.respondText("admin:${call.principal.roles.joinToString(",") { it.name }}")
                    }
                }
            }
        }

        // Admin → 200
        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("admin"))
        }
        assertEquals(HttpStatusCode.OK, adminResp.status)
        assertEquals("admin:Admin,User", adminResp.bodyAsText())

        // Regular user → 403
        val userResp = client.get("/admin") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Forbidden, userResp.status)

        // No auth → 401
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin").status)
    }
}
