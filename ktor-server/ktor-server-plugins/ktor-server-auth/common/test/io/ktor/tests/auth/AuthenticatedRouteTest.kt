/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.delete
import io.ktor.server.auth.get
import io.ktor.server.auth.head
import io.ktor.server.auth.options
import io.ktor.server.auth.patch
import io.ktor.server.auth.post
import io.ktor.server.auth.put
import io.ktor.server.auth.route
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlin.test.*

data class UserPrincipal(val name: String)

class AuthenticatedRouteTest {
    @Test
    fun testAuthenticatedDsl() = testApplication {
        install(Authentication.Companion) {
            basic("auth") {
                validate { credentials ->
                    if (credentials.name == credentials.password) {
                        UserPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            authenticate<UserPrincipal>("auth") {
                get("/user") { principal ->
                    call.respondText("Hello, ${principal.name}")
                }
                post<UserPrincipal, String>("/user") { principal, body ->
                    call.respondText("Hello, ${principal.name}, you said $body")
                }
                patch<UserPrincipal, String>("/user") { principal, body ->
                    call.respondText("PATCH ${principal.name} $body")
                }
                delete("/user") { principal ->
                    call.respondText("DELETE ${principal.name}")
                }
                options("/user") { principal ->
                    call.respondText("OPTIONS ${principal.name}")
                }
                get { principal ->
                    call.respondText("GET ${principal.name} root")
                }
                patch<UserPrincipal, String> { principal, body ->
                    call.respondText("PATCH ${principal.name} root $body")
                }
                delete { principal ->
                    call.respondText("DELETE ${principal.name} root")
                }
                options { principal ->
                    call.respondText("OPTIONS ${principal.name} root")
                }
            }
        }

        val response = client.get("/user") {
            basicAuth("test", "test")
        }
        assertEquals("Hello, test", response.bodyAsText())

        val postResponse = client.post("/user") {
            basicAuth("test", "test")
            setBody("Ktor")
        }
        assertEquals("Hello, test, you said Ktor", postResponse.bodyAsText())

        val patchResponse = client.patch("/user") {
            basicAuth("test", "test")
            setBody("Ktor")
        }
        assertEquals("PATCH test Ktor", patchResponse.bodyAsText())

        val deleteResponse = client.delete("/user") {
            basicAuth("test", "test")
        }
        assertEquals("DELETE test", deleteResponse.bodyAsText())

        val optionsResponse = client.options("/user") {
            basicAuth("test", "test")
        }
        assertEquals("OPTIONS test", optionsResponse.bodyAsText())

        val rootResponse = client.get("/") {
            basicAuth("test", "test")
        }
        assertEquals("GET test root", rootResponse.bodyAsText())

        val patchRootResponse = client.patch("/") {
            basicAuth("test", "test")
            setBody("Ktor")
        }
        assertEquals("PATCH test root Ktor", patchRootResponse.bodyAsText())

        val deleteRootResponse = client.delete("/") {
            basicAuth("test", "test")
        }
        assertEquals("DELETE test root", deleteRootResponse.bodyAsText())

        val optionsRootResponse = client.options("/") {
            basicAuth("test", "test")
        }
        assertEquals("OPTIONS test root", optionsRootResponse.bodyAsText())
    }

    @Test
    fun testNestedAuthenticatedDsl() = testApplication {
        install(Authentication.Companion) {
            basic("auth") {
                validate { credentials ->
                    if (credentials.name == credentials.password) {
                        UserPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            authenticate<UserPrincipal>("auth") {
                route("/api") {
                    put("/user") { principal, body: String ->
                        assertEquals("ktor", body)
                        call.respondText("PUT ${principal.name}")
                    }
                    route("/user") {
                        head { principal ->
                            call.respondText("HEAD ${principal.name}")
                        }
                    }
                }
            }
        }

        val response = client.put("/api/user") {
            basicAuth("test", "test")
            setBody("ktor")
        }
        assertEquals("PUT test", response.bodyAsText())

        val headResponse = client.head("/api/user") {
            basicAuth("test", "test")
        }
        assertEquals("HEAD test", headResponse.bodyAsText())
    }

    @Test
    fun testAuthenticatedDslUnauthorized() = testApplication {
        install(Authentication.Companion) {
            basic("auth") {
                validate { credentials ->
                    if (credentials.name == credentials.password) {
                        UserPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            authenticate<UserPrincipal>("auth") {
                get("/user") { _ ->
                    require(false) { "Unreachable" }
                }
            }
        }

        val response = client.get("/user")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("", response.bodyAsText())
    }
}
