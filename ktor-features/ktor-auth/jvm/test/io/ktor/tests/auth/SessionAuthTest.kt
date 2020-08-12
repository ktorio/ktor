/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.call.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import kotlinx.coroutines.*
import kotlin.test.*

class SessionAuthTest {
    @Test
    fun testSessionOnly() {
        withTestApplication {
            application.install(Sessions) {
                cookie<MySession>("S")
            }
            application.install(Authentication) {
                session<MySession> {
                    validate { it }
                    challenge {
                        call.respond(UnauthorizedResponse())
                    }
                }
            }

            application.routing {
                authenticate {
                    get("/") { call.respondText("Secret info") }
                    get("/logout") {
                        call.sessions.clear<MySession>()
                        call.respondRedirect("/")
                    }
                    get("/child/logout") {
                        call.sessions.clear<MySession>()
                        call.respondRedirect("/")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            runBlocking {
                val cookieStorage = AcceptAllCookiesStorage()

                client.config {
                    expectSuccess = false
                    install(HttpCookies) {
                        storage = cookieStorage
                    }
                }.use { client ->
                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
                    )

                    val first = client.get<HttpResponse>("/child/logout")
                    first.receive<String>()
                    assertEquals(HttpStatusCode.Unauthorized, first.status)

                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
                    )

                    val second = client.get<HttpResponse>("logout")
                    second.receive<String>()
                    assertEquals(HttpStatusCode.Unauthorized, second.status)
                }
            }
        }
    }

    @Test
    fun testSessionOnlyDeprecated() {
        withTestApplication {
            application.install(Sessions) {
                cookie<MySession>("S")
            }
            @Suppress("DEPRECATION_ERROR")
            application.install(Authentication) {
                session<MySession>(challenge = SessionAuthChallenge.Unauthorized)
            }

            application.routing {
                authenticate {
                    get("/") { call.respondText("Secret info") }
                    get("/logout") {
                        call.sessions.clear<MySession>()
                        call.respondRedirect("/")
                    }
                    get("/child/logout") {
                        call.sessions.clear<MySession>()
                        call.respondRedirect("/")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            runBlocking {
                val cookieStorage = AcceptAllCookiesStorage()

                client.config {
                    expectSuccess = false
                    install(HttpCookies) {
                        storage = cookieStorage
                    }
                }.use { client ->
                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
                    )

                    val first = client.get<HttpResponse>("/child/logout")
                    first.receive<String>()
                    assertEquals(HttpStatusCode.Unauthorized, first.status)

                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
                    )

                    val second = client.get<HttpResponse>("logout")
                    second.receive<String>()
                    assertEquals(HttpStatusCode.Unauthorized, second.status)
                }
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
                    challenge("/login")
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

            handleRequest(HttpMethod.Get, "/") {
                addHeader("Cookie", "S=${defaultSessionSerializer<MySession>().serialize(MySession(1))}")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    data class MySession(val id: Int) : Principal
}
