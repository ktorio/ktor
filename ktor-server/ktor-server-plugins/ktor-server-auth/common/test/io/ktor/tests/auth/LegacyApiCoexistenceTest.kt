/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
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
                get("/new") { call.respondText(call.principal.email) }
                get("/new-call") {
                    // call.principal<T>() also works inside typesafe API
                    val manual = call.principal<TestUser>()
                    val sameRef = if (manual === call.principal) "Yes" else "No"
                    call.respondText("${call.principal.name}:${manual?.name}:$sameRef")
                }
            }
            authenticateWithAnyOf(newScheme) {
                get("anyof/same-call") {
                    val text = if (call.principal<TestUser>() === call.principal) "Yes" else "No"
                    call.respond(text)
                }
            }
        }

        val auth = basicAuthHeader("user")

        val oldResp = client.get("/old") { header(HttpHeaders.Authorization, auth) }
        assertEquals("old@test.com", oldResp.bodyAsText())

        val newResp = client.get("/new") { header(HttpHeaders.Authorization, auth) }
        assertEquals("new@test.com", newResp.bodyAsText())

        val callResp = client.get("/new-call") { header(HttpHeaders.Authorization, auth) }
        assertEquals("user:user:Yes", callResp.bodyAsText())

        val sameCallResp = client.get("anyof/same-call") { header(HttpHeaders.Authorization, auth) }
        assertEquals("Yes", sameCallResp.bodyAsText())
    }

    @Test
    fun `legacy authenticate around authenticateWith requires both principals`() = testApplication {
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
                        call.respondText("${legacy?.name}:${call.principal.name}")
                    }
                }
            }
        }

        val onlyLegacy = client.post("/nested") {
            formCredentials("legacy")
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyLegacy.status)

        val onlyTyped = client.post("/nested") {
            header(HttpHeaders.Authorization, basicAuthHeader("typed"))
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyTyped.status)

        val both = client.post("/nested") {
            formCredentials("legacy")
            header(HttpHeaders.Authorization, basicAuthHeader("typed"))
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("legacy:typed", both.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/nested").status)
    }

    @Test
    fun `required authenticate layers around authenticateWith are all enforced`() = testApplication {
        install(Authentication) {
            form("outer-legacy") {
                validate { credentials ->
                    TestUser(credentials.name, "outer@test.com").takeIf { credentials.name == "outer" }
                }
            }
            bearer("inner-legacy") {
                authHeader { call ->
                    call.request.headers["X-Inner-Token"]?.let { HttpAuthHeader.Single(AuthScheme.Bearer, it) }
                }
                authenticate { credential ->
                    TestUser("inner", "inner@test.com").takeIf { credential.token == "inner" }
                }
            }
        }

        val typedScheme = basic<TestUser>("middle-typed") {
            validate { credentials ->
                TestUser(credentials.name, "typed@test.com").takeIf { credentials.name == "typed" }
            }
        }

        routing {
            authenticate("outer-legacy", strategy = AuthenticationStrategy.Required) {
                authenticateWith(typedScheme) {
                    authenticate("inner-legacy", strategy = AuthenticationStrategy.Required) {
                        post("/nested") {
                            val outer = call.principal<TestUser>("outer-legacy")
                            val inner = call.principal<TestUser>("inner-legacy")
                            call.respondText("${outer?.name}:${call.principal.name}:${inner?.name}")
                        }
                    }
                }
            }
        }

        suspend fun post(outer: Boolean, typed: Boolean, inner: Boolean) = client.post("/nested") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            if (outer) setBody("user=outer&password=p")
            if (typed) header(HttpHeaders.Authorization, basicAuthHeader("typed"))
            if (inner) header("X-Inner-Token", "inner")
        }

        val all = post(outer = true, typed = true, inner = true)
        assertEquals(HttpStatusCode.OK, all.status)
        assertEquals("outer:typed:inner", all.bodyAsText())

        // The typed principal does not make the surrounding required legacy layers optional.
        assertEquals(HttpStatusCode.Unauthorized, post(outer = false, typed = true, inner = true).status)
        assertEquals(HttpStatusCode.Unauthorized, post(outer = true, typed = true, inner = false).status)
        assertEquals(HttpStatusCode.Unauthorized, post(outer = true, typed = false, inner = true).status)
    }

    @Test
    fun `legacy authenticate inside authenticateWith is skipped once typed principal is set`() = testApplication {
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
                authenticate("legacy") {
                    post("/nested") {
                        val typed = call.principal
                        val typedByName = call.principal<TestUser>("outer-new")
                        val legacy = call.principal<TestUser>("legacy")
                        call.respondText("${typed.email}:${typedByName?.email}:${legacy?.email}")
                    }
                }
            }
        }

        val auth = basicAuthHeader("typed")

        // Legacy authenticate keeps Ktor's FirstSuccessful behavior and skips once typed auth set a principal.
        val onlyTyped = client.post("/nested") { header(HttpHeaders.Authorization, auth) }
        assertEquals(HttpStatusCode.OK, onlyTyped.status)
        assertEquals("typed@test.com:typed@test.com:null", onlyTyped.bodyAsText())

        val onlyLegacy = client.post("/nested") {
            formCredentials("legacy")
        }
        assertEquals(HttpStatusCode.Unauthorized, onlyLegacy.status)

        val both = client.post("/nested") {
            formCredentials("legacy")
            header(HttpHeaders.Authorization, auth)
        }
        assertEquals(HttpStatusCode.OK, both.status)
        assertEquals("typed@test.com:typed@test.com:null", both.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.post("/nested").status)
    }
}
