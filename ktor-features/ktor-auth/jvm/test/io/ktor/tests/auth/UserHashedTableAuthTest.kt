/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.random.*
import kotlin.test.*

class UserHashedTableAuthTest {
    private val randomSaltPart = Random.nextInt(0, 0x10000)
        .toString(radix = 16)
        .padStart(4, '0')

    // for test we don't care of hash stability so we append random part to the hash salt
    // for production, you should keep salt part in secret and never use constant salt
    // please see the documentation for explanation and read related articles for best practices
    private val digestFunction = getDigestFunction("SHA-256") { "ktor-$randomSaltPart-${it.length}" }

    @Test
    fun testConfigInlined() {
        testSingle(
            UserHashedTableAuth(
                table = mapOf(
                    "test" to digestFunction("test")
                ),
                digester = digestFunction
            )
        )
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
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/redirect").let { result ->
                assertEquals(HttpStatusCode.Found, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny?user=test&pass=test").let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny", "test", "bad-pass").let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny?bad-user=bad-pass", "test").let { result ->
                assertEquals(HttpStatusCode.Unauthorized, result.response.status())
                assertEquals(null, result.response.content)
            }
            handlePost("/deny", "test", "test").let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    private fun TestApplicationEngine.handlePost(
        uri: String,
        user: String? = null,
        password: String? = null
    ): TestApplicationCall {
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
