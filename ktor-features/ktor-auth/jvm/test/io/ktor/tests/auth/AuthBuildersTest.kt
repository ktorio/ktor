/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import java.util.*
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

    @Test
    fun testMultipleConfigurationsNested() {
        withTestApplication {
            application.install(Authentication) {
                form("first") { validate { c -> if (c.name == "first") UserIdPrincipal(c.name) else null } }
                basic("second") { validate { c -> if (c.name == "second") UserIdPrincipal(c.name) else null } }
            }

            application.routing {
                authenticate("first") {
                    authenticate("second") {
                        route("/both") {
                            handle {
                                assertEquals("first", call.authentication.principal<UserIdPrincipal>()?.name)
                                call.respondText("OK")
                            }
                        }
                        route("/{name}") {
                            handle {
                                assertEquals(
                                    call.parameters["name"],
                                    call.authentication.principal<UserIdPrincipal>()?.name
                                )
                                call.respondText("OK")
                            }
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/nobody") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=nobody&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/first") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/second") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testMultipleConfigurations() {
        withTestApplication {
            application.install(Authentication) {
                form("first") { validate { c -> if (c.name == "first") UserIdPrincipal(c.name) else null } }
                basic("second") { validate { c -> if (c.name == "second") UserIdPrincipal(c.name) else null } }
            }

            application.routing {
                authenticate("first", "second") {
                    route("/both") {
                        handle {
                            assertEquals("first", call.authentication.principal<UserIdPrincipal>()?.name)
                            call.respondText("OK")
                        }
                    }
                    route("/{name}") {
                        handle {
                            assertEquals(
                                call.parameters["name"],
                                call.authentication.principal<UserIdPrincipal>()?.name
                            )
                            call.respondText("OK")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Post, "/nobody") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=nobody&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/first") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Get, "/second") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", Base64.getEncoder().encodeToString("second:".toByteArray())).render()
                )
                setBody("user=first&password=p")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testNoRouting() {
        withTestApplication {
            val auth = application.install(Authentication) {
                form { validate { UserIdPrincipal(it.name) } }
            }

            auth.interceptPipeline(application)

            application.intercept(ApplicationCallPipeline.Call) {
                call.respondText("OK")
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=any&password=")
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }
        }
    }

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

    @Test
    fun testModifyingAuthentication() = withTestApplication {
        application.authentication {
            basic("1") {
                validate { it.name.takeIf { it == "aaa" }?.let { UserIdPrincipal(it) } }
            }
        }

        on("add a new auth method") {
            application.authentication {
                form("2") {
                    validate { it.name.takeIf { it == "bbb" }?.let { UserIdPrincipal(it) } }
                }
            }
        }

        on("auth method name conflict") {
            application.authentication {
                assertFails {
                    basic("2") {
                    }
                }
            }
        }

        application.routing {
            authenticate("1", "2") {
                get("/") {
                    call.respondText(call.principal<UserIdPrincipal>()?.name ?: "?")
                }
            }
        }

        on("attempt to auth") {
            fun TestApplicationRequest.addFormAuth(name: String) {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody("user=$name&password=")
            }

            on("try first auth provider") {
                val call = handleRequest(HttpMethod.Get, "/") {
                    addBasicAuth("aaa")
                }
                assertEquals("aaa", call.response.content)
            }
            on("try second auth provider") {
                val call = handleRequest(HttpMethod.Get, "/") {
                    addFormAuth("bbb")
                }
                assertEquals("bbb", call.response.content)
            }
            on("try invalid user name") {
                val call = handleRequest(HttpMethod.Get, "/") {
                    addBasicAuth("unknown")
                }
                assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
            }
        }
    }

    @Test
    fun testAuthenticateOptionally() {
        withTestApplication {
            application.apply {
                authentication {
                    basic {
                        validate { it.name.takeIf { it == "aaa" }?.let { UserIdPrincipal(it) } }
                    }
                }
                routing {
                    authenticate(optional = true) {
                        get("/auth") {
                            call.respond("OK:${call.authentication.principal<UserIdPrincipal>()?.name}")
                        }
                    }
                }

                on("try call with authentication") {
                    val call = handleRequest(HttpMethod.Get, "/auth") {
                        addBasicAuth("aaa")
                    }
                    assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
                    assertEquals("OK:aaa", call.response.content)
                }
                on("try call with bad authentication") {
                    val call = handleRequest(HttpMethod.Get, "/auth") {
                        addBasicAuth("unknown")
                    }
                    assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
                }
                on("try call without authentication") {
                    val call = handleRequest(HttpMethod.Get, "/auth") {
                    }
                    assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
                    assertEquals("OK:null", call.response.content)
                }
            }
        }
    }

    @Test
    fun testAuthProviderFailureNoChallenge(): Unit = withTestApplication<Unit> {
        class CustomErrorCause : AuthenticationFailedCause.Error("custom error")

        @Suppress("DEPRECATION", "unused")
        class DeprecationTest : AuthenticationFailedCause.Error(cause = "deprecated") {
            fun f(): String = cause
        }

        application.apply {
            authentication {
                provider("custom") {
                    pipeline.intercept(AuthenticationPipeline.CheckAuthentication) {
                        context.authentication.error(this, AuthenticationFailedCause.Error("test"))
                    }
                }
                provider("custom-inheritance") {
                    pipeline.intercept(AuthenticationPipeline.CheckAuthentication) {
                        context.authentication.error(this, CustomErrorCause())
                    }
                }
            }
            routing {
                authenticate("custom") {
                    get("/fail") {
                        fail("shouldn't reach here")
                    }
                }
                authenticate("custom", optional = true) {
                    get("/pass") {
                        call.respondText("OK")
                    }
                }
                authenticate("custom-inheritance") {
                    get("/fail-inheritance") {
                        fail("shouldn't reach here")
                    }
                }
                authenticate("custom-inheritance", optional = true) {
                    get("/pass-inheritance") {
                        call.respondText("OK")
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/fail").let { call ->
            assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
        }

        handleRequest(HttpMethod.Get, "/pass").let { call ->
            assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            assertEquals("OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/fail-inheritance").let { call ->
            assertEquals(HttpStatusCode.Unauthorized.value, call.response.status()?.value)
        }

        handleRequest(HttpMethod.Get, "/pass-inheritance").let { call ->
            assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            assertEquals("OK", call.response.content)
        }
    }

    @Test
    fun testAuthProviderFailureWithChallenge(): Unit = withTestApplication<Unit> {
        application.apply {
            authentication {
                provider("custom") {
                    pipeline.intercept(AuthenticationPipeline.CheckAuthentication) {
                        context.authentication.challenge(this, AuthenticationFailedCause.Error("test")) {
                            call.respondText("Challenge")
                            it.complete()
                        }
                    }
                }
            }
            routing {
                authenticate("custom") {
                    get("/fail") {
                        fail("shouldn't reach here")
                    }
                }
                authenticate("custom", optional = true) {
                    get("/pass") {
                        call.respondText("OK")
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/fail").let { call ->
            assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            assertEquals("Challenge", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/pass").let { call ->
            assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            assertEquals("OK", call.response.content)
        }
    }

    @Test
    fun testAuthDoesntChangeRoutePriority(): Unit = withTestApplication<Unit> {
        application.apply {
            application.install(Authentication) {
                form { validate { c -> UserIdPrincipal(c.name) } }
            }

            routing {
                get("/bar:{baz}") {
                    call.respondText("bar")
                }
                authenticate {
                    get("/{baz}") {
                        call.respondText("baz")
                    }
                }
                get("/foo:{baz}") {
                    call.respondText("foo")
                }
            }
        }

        handleRequest(HttpMethod.Get, "/foo:asd") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("foo", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/bar:asd") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("bar", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/baz") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=username&password=p")
        }.let { call ->
            assertEquals("baz", call.response.content)
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
