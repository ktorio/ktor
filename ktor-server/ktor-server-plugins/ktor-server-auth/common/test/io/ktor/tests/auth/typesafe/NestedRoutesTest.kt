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
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class NestedRoutesTest {

    private val basicScheme = acceptAllBasicScheme("nested-basic")
    private val bearerScheme = testBearerScheme("nested-bearer")

    private val roleScheme = acceptAllBasicScheme("nested-role").withRoles { principal ->
        when (principal.name) {
            "admin" -> setOf(TestRole.Admin, TestRole.User)
            else -> setOf(TestRole.User)
        }
    }

    @Test
    fun `sibling routes with different schemes and scheme reuse`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                get("/basic") { call.respondText("basic:${principal.name}") }
            }
            authenticateWith(bearerScheme) {
                get("/bearer") { call.respondText("bearer:${principal.name}") }
            }
            authenticateWith(basicScheme) {
                get("/basic2") { call.respondText("basic2:${principal.name}") }
            }
        }

        val basicResp = client.get("/basic") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals("basic:user", basicResp.bodyAsText())

        val bearerResp = client.get("/bearer") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        assertEquals("bearer:bearer-user", bearerResp.bodyAsText())

        // Same scheme registered once, works on both routes
        val basic2Resp = client.get("/basic2") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals("basic2:alice", basic2Resp.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/basic").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/bearer").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/basic2").status)

        val basicWithBearerResp = client.get("/basic") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        assertEquals(HttpStatusCode.Unauthorized, basicWithBearerResp.status)

        val bearerWithBasicResp = client.get("/bearer") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.Unauthorized, bearerWithBasicResp.status)

        val basic2WithBearerResp = client.get("/basic2") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        assertEquals(HttpStatusCode.Unauthorized, basic2WithBearerResp.status)
    }

    @Test
    fun `nested and deeply nested routes inherit authentication`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                route("/api") {
                    get("/users") { call.respondText("users:${principal.name}") }
                    get("/items") { call.respondText("items:${principal.name}") }
                    route("/v2") {
                        route("/admin") {
                            get("/deep") { call.respondText("deep:${principal.name}") }
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
                val outerContext = authenticatedContext()

                authenticateWith(innerScheme) {
                    post("/nested") {
                        val outer = outerContext.principal(this)
                        call.respondText("${outer.name}:${principal.name}")
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
                        call.respondText("admin:${roles.joinToString(",") { it.name }}")
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
