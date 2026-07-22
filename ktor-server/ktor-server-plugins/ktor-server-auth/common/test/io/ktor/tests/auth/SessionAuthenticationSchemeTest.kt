/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionAuthenticationSchemeTest {
    @Test
    fun `session scheme authenticates and rejects`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("test-session") {
            validate { session ->
                TestUser(session.username, "${session.username}@test.com")
            }
        }

        routing {
            install(sessionScheme)
            get("/set-session") {
                sessionScheme.setSession(UserSession("Alice", visits = 3))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                get("/protected") {
                    call.respondText("${call.session.username}:${call.session.visits}:${call.principal.email}")
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
        assertEquals("Alice:3:Alice@test.com", response.bodyAsText())
    }

    @Test
    fun `session scheme updates and clears typed session`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("test-session-update") {
            validate { session -> TestUser(session.username, "${session.username}@test.com") }
        }

        routing {
            install(sessionScheme)
            get("/set-session") {
                sessionScheme.setSession(UserSession("Alice"))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                get("/touch") {
                    val updated = call.updateSession { current -> current.copy(visits = current.visits + 1) }
                    call.respondText("${call.principal.name}:${updated.visits}:${call.session.visits}")
                }
                get("/rename") {
                    call.session = call.session.copy(username = "bob")
                    call.respondText("${call.principal.name}:${call.session.username}")
                }
                get("/logout") {
                    call.clearSession()
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
    fun `session scheme transforms session before principal resolution`() = testApplication {
        val sessionName = "test-session-transform"
        val sessionScheme = session<UserSession, TestUser>(sessionName) {
            transformSession { session -> session.copy(visits = session.visits + 1) }
            validate { session -> TestUser(session.username, "${session.username}-${session.visits}@test.com") }
        }

        routing {
            install(sessionScheme)
            get("/set-session") {
                sessionScheme.setSession(UserSession("Alice"))
                call.respondText("ok")
            }
            authenticateWith(sessionScheme) {
                get("/protected") {
                    call.respondText("${call.session.username}:${call.session.visits}:${call.principal.email}")
                }
            }
        }

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")

        val first = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("Alice:1:Alice-1@test.com", first.bodyAsText())

        val second = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("Alice:2:Alice-2@test.com", second.bodyAsText())
    }

    @Test
    fun `session scheme requires Sessions to be installed before typed route`() = testApplication {
        val sessionScheme = session<UserSession, UserSession>("missing-session") {
            validate { session -> session }
        }
        routing {
            authenticateWith(sessionScheme) {}
        }
        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `session scheme requires matching Sessions provider before typed route`() = testApplication {
        val sessionScheme = session<UserSession, UserSession>("missing-session-provider") {
            validate { session -> session }
        }
        install(Sessions) {
            cookie<UserSession>("other-session")
        }
        routing {
            authenticateWith(sessionScheme) {}
        }
        val failure = assertFailsWith<IllegalStateException> { startApplication() }
        assertContains(
            failure.message.orEmpty(),
            "requires Sessions to be installed before authenticateWith"
        )
    }

    @Test
    fun `optional session route exposes null sessionOrNull without session`() = testApplication {
        val sessionScheme = session<UserSession, UserSession>("optional-session-or-null") {
            validate { session -> session }
        }

        routing {
            install(sessionScheme)
            get("/set-session") {
                sessionScheme.setSession(UserSession("Alice"))
                call.respondText("ok")
            }
            authenticateWithOptional(sessionScheme) {
                get("/protected") {
                    check(call.principalOrNull?.username == call.sessionOrNull?.username)
                    call.respondText(call.sessionOrNull?.username.orEmpty())
                }
            }
        }

        val withoutSession = client.get("/protected")
        assertEquals(HttpStatusCode.OK, withoutSession.status)
        assertEquals("", withoutSession.bodyAsText())

        val cookieClient = createClient { install(HttpCookies) }
        cookieClient.get("/set-session")
        val withSession = cookieClient.get("/protected")
        assertEquals(HttpStatusCode.OK, withSession.status)
        assertEquals("Alice", withSession.bodyAsText())
    }

    @Test
    fun `session scheme requires Sessions before optional typed route`() = testApplication {
        val sessionScheme = session<UserSession, UserSession>("missing-optional-session") {
            validate { session -> session }
        }

        routing {
            authenticateWithOptional(sessionScheme) {
                get("/protected") {
                    call.respondText(call.principalOrNull?.username.orEmpty())
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
        val sessionScheme = session<UserSession, UserSession>("missing-role-session") {
            validate { session -> session }
        }.withRoles { setOf(TestRole.User) }
        routing {
            authenticateWith(sessionScheme, roles = setOf(TestRole.User)) {}
        }
        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }

    @Test
    fun `session scheme requires Sessions before any-of typed route`() = testApplication {
        val sessionScheme = session<UserSession, TestUser>("missing-any-of-session") {
            validate { session -> TestUser(session.username, "${session.username}@test.com") }
        }
        val userScheme = basic<TestUser>("user-scheme") {
            validate { TestUser(it.name, "${it.name}@test.com") }
        }

        routing {
            authenticateWithAnyOf(sessionScheme, userScheme) {
                get("/protected") {
                    // no call.session is available here
                    call.respondText(call.principal.name)
                }
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            startApplication()
        }

        assertContains(failure.message.orEmpty(), "requires Sessions to be installed before authenticateWith")
    }
}
