package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class AuthBuildersTest {

    @Test
    fun testPrincipalsAccess() {
        val username = "testuser"

        withTestApplication {
            application.install(Authentication) {
                form { validate { c -> UserIdPrincipal(c.name) } }
            }

            application.routing {
                authenticate {
                    route("/") {

                        handle {
                            assertEquals(username, call.authentication.principal<UserIdPrincipal>()?.name)
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=$username&password=p")
            }
        }
    }
}

private class TestCredential : Credential