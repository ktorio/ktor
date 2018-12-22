package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import kotlin.test.*

class SessionAuthTest {
    @Test
    fun testSessionOnly() {
        withTestApplication {
            application.install(Sessions) {
                cookie<MySession>("S")
            }
            application.install(Authentication) {
                session<MySession>(challenge = SessionAuthChallenge.Unauthorized)
            }

            application.routing {
                authenticate {
                    get("/") { call.respondText("Secret info") }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/", {
                addHeader("Cookie", "S=${autoSerializerOf<MySession>().serialize(MySession(1))}")
            }).let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testSessionAndForm() {
        withTestApplication {
            application.install(Sessions) {
                cookie<MySession>("S")
            }
            application.install(Authentication) {
                session<MySession>()
                form("f") {
                    challenge = FormAuthChallenge.Redirect(url = { "/login" })
                    validate { null }
                }
            }

            application.routing {
                authenticate {
                    authenticate("f") {
                        get("/") { call.respondText("Secret info") }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Found.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/", {
                addHeader("Cookie", "S=${autoSerializerOf<MySession>().serialize(MySession(1))}")
            }).let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    data class MySession(val id: Int) : Principal
}