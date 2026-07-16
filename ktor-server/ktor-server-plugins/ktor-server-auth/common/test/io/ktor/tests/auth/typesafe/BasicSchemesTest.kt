/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
data class UserSession(val username: String, val visits: Int = 0)

class BasicSchemesTest {

    private val basicScheme = testBasicScheme()
    private val bearerScheme = testBearerScheme()

    @Test
    fun `basic scheme authenticates and rejects`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                get("/profile") {
                    call.respondText("${call.principal.name}:${call.principal.email}")
                }
            }
        }

        val ok = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("user:user@test.com", ok.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/profile").status)

        val invalid = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `bearer scheme authenticates and rejects`() = testApplication {
        routing {
            authenticateWith(bearerScheme) {
                get("/api") {
                    call.respondText("${call.principal.name}:${call.principal.email}")
                }
            }
        }

        val ok = client.get("/api") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("bearer-user:bearer@test.com", ok.bodyAsText())

        val invalid = client.get("/api") {
            header(HttpHeaders.Authorization, bearerAuthHeader("invalid"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `different typed schemes with same name fail fast`() = testApplication {
        val first = bearer<TestUser>("duplicate-bearer") {
            validate { TestUser("first", "first@test.com") }
        }
        val second = bearer<TestUser>("duplicate-bearer") {
            validate { TestUser("second", "second@test.com") }
        }
        routing {
            authenticateWith(first) {}
            authenticateWith(second) {}
        }
        val failure = assertFailsWith<IllegalArgumentException> {
            startApplication()
        }
        assertContains(failure.message.orEmpty(), "already registered")
    }

    @Test
    fun `multiple schemes can protect multiple route blocks`() = testApplication {
        data class AdminPrincipal(val level: Int)

        val userScheme = basic<TestUser>("user-scheme") {
            validate { TestUser(it.name, "${it.name}@test.com") }
        }
        val adminScheme = bearer<AdminPrincipal>("admin-scheme") {
            validate { AdminPrincipal(42) }
        }

        routing {
            authenticateWith(userScheme) {
                get("/user") { call.respondText(call.principal.email) }
            }
            authenticateWith(adminScheme) {
                get("/admin") { call.respondText("level=${call.principal.level}") }
            }
        }

        val userResp = client.get("/user") {
            header(HttpHeaders.Authorization, basicAuthHeader("Alice"))
        }
        assertEquals("Alice@test.com", userResp.bodyAsText())

        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, bearerAuthHeader("token"))
        }
        assertEquals("level=42", adminResp.bodyAsText())
    }

    @Test
    fun `form scheme authenticates and rejects`() = testApplication {
        val formScheme = form<TestUser>("test-form") {
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(formScheme) {
                post("/login") {
                    call.respondText("${call.principal.name}:${call.principal.email}")
                }
            }
        }

        val ok = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=user&password=pass")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("user:user@test.com", ok.bodyAsText())

        val invalid = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=wrong&password=creds")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `custom auth context is available in authenticated route`() = testApplication {
        val scheme = basic<TestUser>("custom-context") {
            validate { credentials -> TestUser(credentials.name, "${credentials.name}@test.com") }
        }

        routing {
            authenticateWith(scheme) {
                get("/custom") {
                    call.respondText("${call.principal.email}:${call.principal.name}")
                }
            }
        }

        val response = client.get("/custom") {
            header(HttpHeaders.Authorization, basicAuthHeader("Alice"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice@test.com:Alice", response.bodyAsText())
    }
}
