package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class UserHashedTableAuthTest {

    @Test
    fun testConfigInlined() {
        testSingle(UserHashedTableAuth(table = mapOf(
                "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
        )))
    }

    @Test
    fun testConfigParse() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")
        mapConfig.put("auth.users.0.hash", "VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=")
        testSingle(UserHashedTableAuth(mapConfig.config("auth")))
    }

    fun testSingle(hashedUserTable: UserHashedTableAuth) {
        withTestApplication {
            application.routing {
                route("/redirect") {
                    authentication {
                        formAuthentication(
                                challenge = FormAuthChallenge.Redirect({ _, _ -> "/unauthorized" }),
                                validate = { hashedUserTable.authenticate(it) }
                        )
                    }
                    post {
                        call.respondText("ok")
                    }
                }
                route("/deny") {
                    authentication {
                        formAuthentication(
                                validate = { hashedUserTable.authenticate(it) }
                        )
                    }
                    post {
                        call.respondText("ok")
                    }
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

    fun TestApplicationHost.handlePost(uri: String, user: String? = null, password: String? = null): TestApplicationCall {
        return handleRequest(HttpMethod.Post, uri) {
            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            body = ValuesMap.build {
                if (user != null) append("user", user)
                if (password != null) append("password", password)
            }.formUrlEncode()
        }
    }
}