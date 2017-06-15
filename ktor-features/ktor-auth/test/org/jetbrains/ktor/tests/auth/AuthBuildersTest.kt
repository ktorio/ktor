package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

class AuthBuildersTest {

    @Test
    fun testPrincipalsAccess() {
        val username = "testuser"

        withTestApplication {
            application.routing {
                route("/") {
                    authentication {
                        formAuthentication { c -> UserIdPrincipal(c.name) }
                    }

                    handle {
                        assertEquals(username, call.authentication.principal<UserIdPrincipal>()?.name)
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                body = "user=$username&password=p"
            }
        }
    }
}

private class TestCredential : Credential