/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.test.*

@Serializable
data class UserSession(val username: String, val visits: Int = 0)

private class EmailContext(
    defaultContext: DefaultAuthenticatedContext<TestUser>
) : AuthenticatedContext<TestUser> by defaultContext {
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
            cookie(sessionScheme)
        }

        routing {
            get("/set-session") {
                call.sessions.set(sessionScheme, UserSession("Alice"))
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
        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")
        val response = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice", response.bodyAsText())
    }

    @Test
    fun `session scheme exposes stored session and mapped principal`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("test-session-principal") {
            validate { session ->
                TestUser(session.username, "${session.username}@test.com")
            }
        }

        install(Sessions) {
            cookie(sessionScheme)
        }

        routing {
            get("/set-session") {
                call.sessions.set(sessionScheme, UserSession("Alice", visits = 3))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                assertIs<SessionAuthenticatedContext<UserSession, TestUser>>(authenticatedContext())

                get("/protected") {
                    call.respondText("${session.username}:${session.visits}:${principal.email}")
                }
            }
        }

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")
        val response = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice:3:Alice@test.com", response.bodyAsText())
    }

    @Test
    fun `session scheme updates and clears typed session`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("test-session-update") {
            validate { session -> TestUser(session.username, "${session.username}@test.com") }
        }

        install(Sessions) {
            cookie(sessionScheme)
        }

        routing {
            get("/set-session") {
                call.sessions.set(sessionScheme, UserSession("Alice"))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                get("/touch") {
                    val updated = updateSession { current -> current.copy(visits = current.visits + 1) }
                    call.respondText("${principal.name}:${updated.visits}:${session.visits}")
                }
                get("/rename") {
                    session = session.copy(username = "bob")
                    call.respondText("${principal.name}:${session.username}")
                }
                get("/logout") {
                    clearSession()
                    call.respondText("bye")
                }
            }
        }

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")

        val firstTouch = cookieClient.get("/touch")
        assertEquals(HttpStatusCode.OK, firstTouch.status)
        assertEquals("Alice:1:1", firstTouch.bodyAsText())

        val secondTouch = cookieClient.get("/touch")
        assertEquals(HttpStatusCode.OK, secondTouch.status)
        assertEquals("Alice:2:2", secondTouch.bodyAsText())

        val rename = cookieClient.get("/rename")
        assertEquals(HttpStatusCode.OK, rename.status)
        assertEquals("Alice:bob", rename.bodyAsText())

        val afterRename = cookieClient.get("/touch")
        assertEquals(HttpStatusCode.OK, afterRename.status)
        assertEquals("bob:3:3", afterRename.bodyAsText())

        val logout = cookieClient.get("/logout")
        assertEquals(HttpStatusCode.OK, logout.status)
        assertEquals("bye", logout.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, cookieClient.get("/touch").status)
    }

    @Test
    fun `session scheme requires Sessions to be installed before typed route`() = testApplication {
        val sessionScheme = session<UserSession>("missing-session") {
            validate { session -> session }
        }

        routing {
            authenticateWith(sessionScheme) {
                get("/protected") {
                    call.respondText(principal.username)
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `session scheme requires matching Sessions provider before typed route`() = testApplication {
        val sessionScheme = session<UserSession>("missing-session-provider") {
            validate { session -> session }
        }

        install(Sessions) {
            cookie<UserSession>("other-session")
        }

        routing {
            authenticateWith(sessionScheme) {
                get("/protected") {
                    call.respondText(principal.username)
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(
            failure.message.orEmpty(),
            "requires a Sessions provider named `missing-session-provider`"
        )
    }

    @Test
    fun `session scheme requires Sessions before optional typed route`() = testApplication {
        val sessionScheme = session<UserSession>("missing-optional-session") {
            validate { session -> session }
        }

        routing {
            authenticateWith(sessionScheme.optional()) {
                get("/protected") {
                    call.respondText(principal?.username.orEmpty())
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `session scheme requires Sessions before role typed route`() = testApplication {
        val sessionScheme = session<UserSession>("missing-role-session") {
            validate { session -> session }
        }.withRoles {
            setOf(TestRole.User)
        }

        routing {
            authenticateWith(sessionScheme, roles = setOf(TestRole.User)) {
                get("/protected") {
                    call.respondText(principal.username)
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `session scheme requires Sessions before any-of typed route`() = testApplication {
        val sessionScheme = session<UserSession>("missing-any-of-session") {
            validate { session -> session }
        }

        routing {
            authenticateWithAnyOf<UserSession>(sessionScheme) {
                get("/protected") {
                    call.respondText(principal.username)
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
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
            header(HttpHeaders.Authorization, basicAuthHeader("Alice"))
        }
        assertEquals("Alice@test.com", userResp.bodyAsText())

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
            header(HttpHeaders.Authorization, basicAuthHeader("Alice"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice@test.com:Alice", response.bodyAsText())
    }
}
