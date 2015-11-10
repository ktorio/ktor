package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
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

            assertEquals(ApplicationRequestStatus.Handled, response.requestResult)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())

            assertWWWAuthenticateHeaderExist(response)
        }
    }

    @Test
    fun testBasicAuthSuccess() {
        withTestApplication {
            application.configureServer()
            val user = "user1"
            val p = "user1"

            val response = handleRequest {
                uri = "/"

                val up = "$user:$p"
                val encoded = encodeBase64(up.toByteArray(Charsets.ISO_8859_1))
                addHeader(HttpHeaders.Authorization, "Basic $encoded")
            }

            assertEquals(ApplicationRequestStatus.Handled, response.requestResult)
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

            val response = handleRequest {
                uri = "/"

                val up = "$user:$p"
                val encoded = encodeBase64(up.toByteArray(Charsets.ISO_8859_1))
                addHeader(HttpHeaders.Authorization, "Basic $encoded")
            }

            assertEquals(ApplicationRequestStatus.Handled, response.requestResult)
            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
            assertNotEquals("Secret info", response.response.content)

            assertWWWAuthenticateHeaderExist(response)
        }
    }

    private fun assertWWWAuthenticateHeaderExist(response: RequestResult) {
        assertNotNull(response.response.headers[HttpHeaders.WWWAuthenticate])
        val header = parseAuthorizationHeader(response.response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

        assertEquals(AuthScheme.Basic, header.authScheme)
        assertEquals("ktor-test", header.parameter(HttpAuthHeader.Parameters.Realm))
    }

    private fun Application.configureServer() {
        routing {
            route("/") {
                auth {
                    basicAuth()

                    verifyBatchTypedWith { credentials: List<UserPasswordCredential> ->
                        credentials.filter { it.name == it.password }.map { UserIdPrincipal(it.name) }
                    }

                    fail {
                        response.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("ktor-test"))
                    }
                }

                handle {
                    response.sendText("Secret info")
                }
            }
        }
    }
}
