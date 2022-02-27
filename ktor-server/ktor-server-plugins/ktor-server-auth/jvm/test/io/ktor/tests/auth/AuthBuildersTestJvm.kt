/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import java.util.*
import kotlin.test.*

@Suppress("DEPRECATION")
class AuthBuildersTestJvm {

    @Test
    fun testCompleteApplication() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestSession>("S")
            }
            application.install(Authentication) {
                session<TestSession>("S") {
                    challenge {} // a root session provider doesn't send any challenge
                    validate { UserIdPrincipal(it.name) } // but optionally provides an authenticated user
                }
                session<TestSession>("S_web") {
                    challenge("/login") // for web roots it does redirect to login
                    validate { UserIdPrincipal(it.name) } // unfortunately it does check twice
                }
                basic("B") {
                    realm = "test-app"
                    validate { UserIdPrincipal(it.name) }
                }
                form("F") {
                    validate { UserIdPrincipal(it.name) }
                }
            }
            application.routing {
                authenticate("S") {
                    get("/") {
                        val logText = call.principal<UserIdPrincipal>()?.name
                            ?.let { "Logged in as $it." } ?: "Not logged in."
                        call.respondText("Public index. $logText")
                    }
                    route("/user") {
                        authenticate("S_web") {
                            get("profile") {
                                call.respondText("Profile for ${call.principal<UserIdPrincipal>()?.name}.")
                            }
                        }
                        authenticate("B") {
                            get("files/{name...}") {
                                call.respondText(
                                    "File ${call.parameters["name"]} for user " +
                                        "${call.principal<UserIdPrincipal>()?.name}."
                                )
                            }
                        }
                    }
                    get("/login") {
                        val user = call.principal<UserIdPrincipal>()
                        if (user != null) {
                            call.respondRedirect("/")
                        } else {
                            call.respondText("Login form goes here.")
                        }
                    }
                    authenticate("F") {
                        post("/login") {
                            val user = call.principal<UserIdPrincipal>()
                            assertNotNull(user)
                            call.sessions.set(TestSession(user.name))
                            call.respondText("Logged in successfully as ${user.name}.")
                        }
                    }
                }
            }

            val serializedSession = defaultSessionSerializer<TestSession>().serialize(TestSession("tester"))
            val sessionCookieContent = "S=$serializedSession"
            fun TestApplicationRequest.addCookie() {
                addHeader(HttpHeaders.Cookie, sessionCookieContent)
            }

            fun TestApplicationRequest.addFormAuth(name: String, pass: String) {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=$name&password=$pass")
            }

            on("Index should be available for everyone") {
                val call = handleRequest(HttpMethod.Get, "/")
                assertTrue { call.response.status()!!.isSuccess() }
                assertEquals("Public index. Not logged in.", call.response.content)
            }
            on("On the other side the index page should recognize logged in users") {
                val call = handleRequest(HttpMethod.Get, "/") {
                    addCookie()
                }

                assertTrue { call.response.status()!!.isSuccess() }
                assertEquals("Public index. Logged in as tester.", call.response.content)
            }
            on("User profile page should redirect to login page") {
                val call = handleRequest(HttpMethod.Get, "/user/profile")
                assertEquals("/login", call.response.headers[HttpHeaders.Location])
            }
            on("User profile page should be show for an authenticated user") {
                val call = handleRequest(HttpMethod.Get, "/user/profile") {
                    addCookie()
                }
                assertEquals("Profile for tester.", call.response.content)
            }
            on("Login page shouldn't be shown for an authenticated user (with cookies)") {
                val call = handleRequest(HttpMethod.Get, "/login") {
                    addCookie()
                }
                assertEquals(HttpStatusCode.Found.value, call.response.status()?.value)
            }
            on("Login page should be shown for clean user") {
                val call = handleRequest(HttpMethod.Get, "/login")
                assertEquals("Login form goes here.", call.response.content)
            }
            on("Login page should create session on form post") {
                val call = handleRequest(HttpMethod.Post, "/login") {
                    addFormAuth("tester", "")
                }
                val cookies = call.response.headers[HttpHeaders.SetCookie]?.let { parseServerSetCookieHeader(it) }

                assertNotNull(cookies, "Set-Cookie should be sent")
                assertEquals(serializedSession, cookies.value)
                assertEquals("Logged in successfully as tester.", call.response.content)
            }
            on("An authenticated user can download files by a web browser") {
                val call = handleRequest(HttpMethod.Get, "/user/files/doc1.txt") {
                    addCookie()
                }
                assertEquals("File doc1.txt for user tester.", call.response.content)
            }
            on("A download manager or wget/curl tool could download file using basic auth") {
                val firstAttempt = handleRequest(HttpMethod.Get, "/user/files/doc1.txt")
                // with no auth header we should get basic auth challenge
                assertEquals(
                    "Basic realm=test-app, charset=UTF-8",
                    firstAttempt.response.headers[HttpHeaders.WWWAuthenticate]
                )

                // so a download tool should show a prompt so user can provide name and password
                // and retry with basic auth credentials
                val call = handleRequest(HttpMethod.Get, "/user/files/doc1.txt") {
                    addBasicAuth()
                }
                assertEquals("File doc1.txt for user tester.", call.response.content)
            }
        }
    }

    private fun TestApplicationRequest.addBasicAuth(name: String = "tester") {
        addHeader(
            HttpHeaders.Authorization,
            HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("$name:".toByteArray())).render()
        )
    }

    data class TestSession(val name: String)
}
