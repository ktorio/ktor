/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
data class UserSession(val username: String)

private class EmailContext(
    private val config: AuthenticatedContextConfig<TestUser>
) : AuthenticatedContext<TestUser> {
    override fun principal(context: RoutingContext): TestUser = config.principal(context)

    fun email(context: RoutingContext): String = principal(context).email
}

context(auth: EmailContext)
private val RoutingContext.email: String
    get() = auth.email(this)

class BasicSchemesTest {

    private val basicScheme = testBasicScheme()
    private val bearerScheme = testBearerScheme()

    @Test
    fun `basic scheme authenticates and rejects`() = testApplication {
        routing {
            authenticateWith(basicScheme) {
                assertIs<DefaultAuthenticatedContext<TestUser>>(authenticatedContext())

                get("/profile") {
                    call.respondText("${principal.name}:${principal.email}")
                }
            }
        }

        val ok = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("user"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("user:user@test.com", ok.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/profile").status)

        val invalid = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("wrong", "creds"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `bearer scheme authenticates and rejects`() = testApplication {
        routing {
            authenticateWith(bearerScheme) {
                get("/api") {
                    call.respondText("${principal.name}:${principal.email}")
                }
            }
        }

        val ok = client.get("/api") {
            header(HttpHeaders.Authorization, bearerAuthHeader("valid"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("bearer-user:bearer@test.com", ok.bodyAsText())

        val invalid = client.get("/api") {
            header(HttpHeaders.Authorization, bearerAuthHeader("invalid"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `form scheme authenticates and rejects`() = testApplication {
        val formScheme = form<TestUser>("test-form") {
            validate { credentials ->
                if (credentials.name == "user" && credentials.password == "pass") {
                    TestUser(credentials.name, "user@test.com")
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(formScheme) {
                post("/login") {
                    call.respondText("${principal.name}:${principal.email}")
                }
            }
        }

        val ok = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=user&password=pass")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("user:user@test.com", ok.bodyAsText())

        val invalid = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("user=wrong&password=creds")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `session scheme authenticates and rejects`() = testApplication {
        val sessionScheme = session<UserSession>("test-session") {
            validate { session -> session }
        }

        install(Sessions) {
            cookie<UserSession>("test_session")
        }

        routing {
            get("/set-session") {
                call.sessions.set(UserSession("alice"))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                get("/protected") {
                    call.respondText(principal.username)
                }
            }
        }

        // Missing session → 401
        assertEquals(HttpStatusCode.Unauthorized, client.get("/protected").status)

        // With session → 200
        val cookieClient = createClient { install(io.ktor.client.plugins.cookies.HttpCookies) }
        cookieClient.get("/set-session")
        val response = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("alice", response.bodyAsText())
    }

    @Test
    fun `different principal types on different routes are auto-inferred`() = testApplication {
        data class AdminPrincipal(val level: Int)

        val userScheme = basic<TestUser>("user-scheme") {
            validate { TestUser(it.name, "${it.name}@test.com") }
        }
        val adminScheme = bearer<AdminPrincipal>("admin-scheme") {
            authenticate { AdminPrincipal(42) }
        }

        routing {
            authenticateWith(userScheme) {
                get("/user") { call.respondText(principal.email) }
            }
            authenticateWith(adminScheme) {
                get("/admin") { call.respondText("level=${principal.level}") }
            }
        }

        val userResp = client.get("/user") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals("alice@test.com", userResp.bodyAsText())

        val adminResp = client.get("/admin") {
            header(HttpHeaders.Authorization, bearerAuthHeader("token"))
        }
        assertEquals("level=42", adminResp.bodyAsText())
    }

    @Test
    fun `custom auth context is available in authenticated route`() = testApplication {
        val config = TypedBasicAuthConfig<TestUser>().apply {
            validate { credentials -> TestUser(credentials.name, "${credentials.name}@test.com") }
        }
        val scheme = DefaultAuthScheme(
            name = "custom-context",
            principalType = TestUser::class,
            provider = config.buildProvider("custom-context"),
            onUnauthorized = null,
        ) { contextConfig -> EmailContext(contextConfig) }

        routing {
            authenticateWith(scheme) {
                assertIs<EmailContext>(authenticatedContext())

                get("/custom") {
                    call.respondText("$email:${principal.name}")
                }
            }
        }

        val response = client.get("/custom") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("alice@test.com:alice", response.bodyAsText())
    }
}
