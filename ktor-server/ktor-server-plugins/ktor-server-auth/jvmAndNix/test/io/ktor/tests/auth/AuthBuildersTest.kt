/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

@Suppress("DEPRECATION")
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
                    HttpAuthHeader.Single("basic", "second:".encodeBase64()).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", "second:".encodeBase64()).render()
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
                    HttpAuthHeader.Single("basic", "second:".encodeBase64()).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
            }

            handleRequest(HttpMethod.Post, "/both") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                addHeader(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single("basic", "second:".encodeBase64()).render()
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
            application.install(Authentication) {
                form { validate { UserIdPrincipal(it.name) } }
            }

            application.install(AuthenticationInterceptors)

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
                    basic("2") {}
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
                    val call = handleRequest(HttpMethod.Get, "/auth") {}
                    assertEquals(HttpStatusCode.OK.value, call.response.status()?.value)
                    assertEquals("OK:null", call.response.content)
                }
            }
        }
    }

    @Test
    fun testAuthProviderFailureNoChallenge(): Unit = withTestApplication {
        class CustomErrorCause : AuthenticationFailedCause.Error("custom error")

        application.apply {
            authentication {
                provider("custom") {
                    authenticate { context ->
                        context.error(this, AuthenticationFailedCause.Error("test"))
                    }
                }
                provider("custom-inheritance") {
                    authenticate { context ->
                        context.error(this, CustomErrorCause())
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
    fun testAuthProviderFailureWithChallenge(): Unit = withTestApplication {
        application.apply {
            authentication {
                provider("custom") {
                    authenticate { context ->
                        context.challenge(this, AuthenticationFailedCause.Error("test")) { challenge, call ->
                            call.respondText("Challenge")
                            challenge.complete()
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
    fun testAuthDoesntChangeRoutePriority(): Unit = withTestApplication {
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
            HttpAuthHeader.Single("basic", "$name:".encodeBase64()).render()
        )
    }

    data class TestSession(val name: String)
}
