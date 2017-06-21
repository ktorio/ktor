package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class BasicAuthTest {
    @Test
    fun testBasicAuthNoAuth() {
        withTestApplication {
            application.configureServer()

            val response = handleRequest {
                uri = "/"
            }

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
            assertNull(response.response.content)

            assertWWWAuthenticateHeaderExist(response)
        }
    }

    @Test
    fun testBasicAuthNoInterceptor() {
        withTestApplication {
            val user = "user1"
            val p = "user1"

            application.intercept(ApplicationCallPipeline.Infrastructure) {
                val authInfo = call.request.basicAuthenticationCredentials()
                assertNotNull(authInfo)
                assertEquals(authInfo, call.basicAuthenticationCredentials())

                assertEquals(user, authInfo!!.name)
                assertEquals(p, authInfo.password)

                call.response.status(HttpStatusCode.OK)
                call.respondText("ok")
            }

            val response = handleRequestWithBasic("/", user, p)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertEquals("ok", response.response.content)
        }
    }

    @Test
    fun testBasicAuthSuccess() {
        withTestApplication {
            application.configureServer()
            val user = "user1"
            val p = "user1"

            val response = handleRequestWithBasic("/", user, p)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.OK, response.response.status())
            assertEquals("Secret info", response.response.content)
        }
    }

    @Test
    fun testBasicAuthFailed() {
        withTestApplication {
            application.configureServer()
            val user = "user1"
            val p = "wrong password"

            val response = handleRequestWithBasic("/", user, p)

            assertTrue(response.requestHandled)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
            assertNotEquals("Secret info", response.response.content)

            assertWWWAuthenticateHeaderExist(response)
        }
    }

    @Test
    fun testSimplifiedFlow() {
        withTestApplication {
            application.routing {
                route("/") {
                    authentication {
                        basicAuthentication("ktor-test") { c -> if (c.name == "good") UserIdPrincipal(c.name) else null }
                    }

                    handle {
                        call.respondText("Secret info")
                    }
                }
            }

            handleRequestWithBasic("/", "bad", "").let { response ->
                assertTrue(response.requestHandled)
                assertEquals(HttpStatusCode.Unauthorized, response.response.status())
                assertNotEquals("Secret info", response.response.content)

                assertWWWAuthenticateHeaderExist(response)
            }

            handleRequestWithBasic("/", "good", "").let { response ->
                assertTrue(response.requestHandled)
                assertEquals(HttpStatusCode.OK, response.response.status())
                assertEquals("Secret info", response.response.content)
            }
        }
    }

    private fun TestApplicationHost.handleRequestWithBasic(url: String, user: String, pass: String) =
            handleRequest {
                uri = url

                val up = "$user:$pass"
                val encoded = encodeBase64(up.toByteArray(Charsets.ISO_8859_1))
                addHeader(HttpHeaders.Authorization, "Basic $encoded")
            }

    private fun assertWWWAuthenticateHeaderExist(response: ApplicationCall) {
        assertNotNull(response.response.headers[HttpHeaders.WWWAuthenticate])
        val header = parseAuthorizationHeader(response.response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

        assertEquals(AuthScheme.Basic, header.authScheme)
        assertEquals("ktor-test", header.parameter(HttpAuthHeader.Parameters.Realm))
    }

    private fun Application.configureServer() {
        routing {
            route("/") {
                authentication {
                    basicAuthentication("ktor-test") {
                        if (it.name == it.password)
                            UserIdPrincipal(it.name)
                        else
                            null // fail!
                    }
                }

                handle {
                    call.respondText("Secret info")
                }
            }
        }
    }
}
