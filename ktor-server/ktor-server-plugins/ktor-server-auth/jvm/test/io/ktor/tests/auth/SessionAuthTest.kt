/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*

@Suppress("DEPRECATION")
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

                    val first = client.get("/child/logout")
                    first.body<String>()
                    assertEquals(HttpStatusCode.Unauthorized, first.status)

                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", defaultSessionSerializer<MySession>().serialize(MySession(1)), path = "/")
                    )

                    val second = client.get("logout")
                    second.body<String>()
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
