/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyApiCoexistenceTest {

    @Test
    fun `typesafe and legacy auth coexist with call principal access`() = testApplication {
        install(Authentication) {
            basic("old-style") {
                validate { TestUser(it.name, "old@test.com") }
            }
        }

        val newScheme = basic<TestUser>("new-style") {
            validate { TestUser(it.name, "new@test.com") }
        }

        routing {
            authenticate("old-style") {
                get("/old") {
                    val p = call.principal<TestUser>()
                    call.respondText(p?.email ?: "none")
                }
            }
            authenticateWith(newScheme) {
                get("/new") { call.respondText(principal.email) }
                get("/new-call") {
                    // call.principal<T>() also works inside typesafe API
                    val manual = call.principal<TestUser>()
                    call.respondText("${principal.name}:${manual?.name}")
                }
            }
        }

        val auth = basicAuthHeader("user")

        val oldResp = client.get("/old") { header(HttpHeaders.Authorization, auth) }
        assertEquals("old@test.com", oldResp.bodyAsText())

        val newResp = client.get("/new") { header(HttpHeaders.Authorization, auth) }
        assertEquals("new@test.com", newResp.bodyAsText())

        val callResp = client.get("/new-call") { header(HttpHeaders.Authorization, auth) }
        assertEquals("user:user", callResp.bodyAsText())
    }

    @Test
    fun `authentication plugin auto-installed by typesafe API`() = testApplication {
        val scheme = basic<TestUser>("auto-install") {
            validate { TestUser(it.name, "auto@test.com") }
        }

        routing {
            authenticateWith(scheme) {
                get("/test") { call.respondText(principal.name) }
            }
        }

        val response = client.get("/test") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user", response.bodyAsText())
    }

    @Test
    fun `authenticateWith inside authenticate`() = testApplication {
        install(Authentication) {
            form("legacy") {
                validate { credentials ->
                    if (credentials.name == "legacy" && credentials.password == "pass") {
                        TestUser(credentials.name, "legacy@test.com")
                    } else {
                        null
                    }
                }
            }
        }

        val newScheme = basic<TestUser>("inner-new") {
            validate { credentials ->
                if (credentials.name == "typed" && credentials.password == "pass") {
                    TestUser(credentials.name, "new@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticate("legacy") {
                authenticateWith(newScheme) {
                    post("/nested") {
                        val legacy = call.principal<TestUser>("legacy")
                        call.respondText("${legacy?.name}:${principal.name}")
                    }
                }
            }
        }

        val onlyLegacy = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=legacy&password=pass")
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyLegacy.status)

        val onlyTyped = client.post("/nested") {
            header(HttpHeaders.Authorization, basicAuthHeader("typed"))
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyTyped.status)

        val both = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            header(HttpHeaders.Authorization, basicAuthHeader("typed"))
            setBody("user=legacy&password=pass")
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("legacy:typed", both.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/nested").status)
    }

    @Test
    fun `authenticate inside authenticateWith`() = testApplication {
        install(Authentication) {
            form("legacy") {
                validate { credentials ->
                    if (credentials.name == "legacy" && credentials.password == "pass") {
                        TestUser(credentials.name, "legacy@test.com")
                    } else {
                        null
                    }
                }
            }
        }

        val newScheme = basic<TestUser>("outer-new") {
            validate { credentials ->
                if (credentials.name == "typed" && credentials.password == "pass") {
                    TestUser(credentials.name, "typed@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(newScheme) {
                get("/outer") { call.respondText(principal.email) }
                authenticate("legacy") {
                    post("/nested") {
                        val typed = principal
                        val typedByName = call.principal<TestUser>("outer-new")
                        val legacy = call.principal<TestUser>("legacy")
                        call.respondText("${typed.email}:${typedByName?.email}:${legacy?.email}")
                    }
                }
            }
        }

        val auth = basicAuthHeader("typed")

        // Outer scope works with typesafe principal
        val outerResp = client.get("/outer") { header(HttpHeaders.Authorization, auth) }
        assertEquals("typed@test.com", outerResp.bodyAsText())

        // Legacy authenticate keeps Ktor's FirstSuccessful behavior and skips once typed auth set a principal.
        val onlyTyped = client.post("/nested") { header(HttpHeaders.Authorization, auth) }
        assertEquals(HttpStatusCode.OK, onlyTyped.status)
        assertEquals("typed@test.com:typed@test.com:null", onlyTyped.bodyAsText())

        val onlyLegacy = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=legacy&password=pass")
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyLegacy.status)

        val both = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            header(HttpHeaders.Authorization, auth)
            setBody("user=legacy&password=pass")
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("typed@test.com:typed@test.com:null", both.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/nested").status)
    }
}
