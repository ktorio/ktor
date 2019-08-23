/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
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
                session<MySession>() {
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
                addHeader("Cookie", "S=${autoSerializerOf<MySession>().serialize(MySession(1))}")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            runBlocking {
                val cookieStorage = AcceptAllCookiesStorage()

                HttpClient(TestHttpClientEngine.create { this.app = this@withTestApplication }) {
                    expectSuccess = false
                    install(HttpCookies) {
                        storage = cookieStorage
                    }
                }.use { client ->
                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", autoSerializerOf<MySession>().serialize(MySession(1)), path = "/")
                    )

                    client.get<HttpResponse>("/child/logout").let { response ->
                        val body = response.receive<String>()
                        println(body)
                        assertEquals(HttpStatusCode.Unauthorized, response.status)
                    }

                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", autoSerializerOf<MySession>().serialize(MySession(1)), path = "/")
                    )

                    client.get<HttpResponse>("logout").let { response ->
                        val body = response.receive<String>()
                        println(body)
                        assertEquals(HttpStatusCode.Unauthorized, response.status)
                    }
                }
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSessionOnlyDeprecated() {
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
                addHeader("Cookie", "S=${autoSerializerOf<MySession>().serialize(MySession(1))}")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            runBlocking {
                val cookieStorage = AcceptAllCookiesStorage()

                HttpClient(TestHttpClientEngine.create { this.app = this@withTestApplication }) {
                    expectSuccess = false
                    install(HttpCookies) {
                        storage = cookieStorage
                    }
                }.use { client ->
                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", autoSerializerOf<MySession>().serialize(MySession(1)), path = "/")
                    )

                    client.get<HttpResponse>("/child/logout").let { response ->
                        val body = response.receive<String>()
                        println(body)
                        assertEquals(HttpStatusCode.Unauthorized, response.status)
                    }

                    cookieStorage.addCookie(
                        "/",
                        Cookie("S", autoSerializerOf<MySession>().serialize(MySession(1)), path = "/")
                    )

                    client.get<HttpResponse>("logout").let { response ->
                        val body = response.receive<String>()
                        println(body)
                        assertEquals(HttpStatusCode.Unauthorized, response.status)
                    }
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

            handleRequest(HttpMethod.Get, "/", {
                addHeader("Cookie", "S=${autoSerializerOf<MySession>().serialize(MySession(1))}")
            }).let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    data class MySession(val id: Int) : Principal
}
