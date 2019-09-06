/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class UserHashedTableAuthTest {

    @Test
    fun testConfigInlined() {
        testSingle(UserHashedTableAuth(table = mapOf(
                "test" to "VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=".decodeBase64Bytes() // sha256 for "test"
        ), digester = getDigestFunction("SHA-256") { "ktor" }))
    }

    private fun testSingle(hashedUserTable: UserHashedTableAuth) {
        withTestApplication {
            application.install(Authentication) {
                form {
                    challenge("/unauthorized")
                    validate { hashedUserTable.authenticate(it) }
                }
                form("checkOnly") {
                    validate { hashedUserTable.authenticate(it) }
                }
            }

            application.routing {
                authenticate {
                    post("/redirect") { call.respondText("ok") }
                }
                authenticate("checkOnly") {
                    post("/deny") { call.respondText("ok") }
                }
            }

            handlePost("/deny").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/redirect").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Found, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny?user=test&pass=test").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny", "test", "bad-pass").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny?bad-user=bad-pass", "test").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny", "test", "test").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    private fun TestApplicationEngine.handlePost(uri: String, user: String? = null, password: String? = null): TestApplicationCall {
        return handleRequest(HttpMethod.Post, uri) {
            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                    Parameters.build {
                        if (user != null) append("user", user)
                        if (password != null) append("password", password)
                    }.formUrlEncode()
            )
        }
    }
}
