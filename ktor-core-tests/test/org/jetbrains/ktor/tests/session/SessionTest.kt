package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class SessionTest {
    @Test
    fun testSessionInlineValue() {
        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieByValue()
            }

            application.routing {
                get("/0") {
                    call.respondText("It should be no session started")
                }
                get("/1") {
                    var session: TestUserSession? = call.sessionOrNull()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        call.session<TestUserSession>() // no no-arg constructor
                    }
                    assertFailsWith(IllegalArgumentException::class) {
                        call.session<EmptySession>() // bad class
                    }

                    call.session(TestUserSession("id1", emptyList()))
                    session = call.session()
                    assertNotNull(session)

                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), call.session<TestUserSession>())

                    call.respondText("ok, ${call.session<TestUserSession>().userId}")
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { response ->
                assertNull(response.response.cookies["SESSION"], "It should be no session set by default")
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
            }.let { response ->
                assertEquals("ok, id1", response.response.content)
            }
        }
    }

    @Test
    fun testDigestSession() {
        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieByValue {
                    settings = SessionCookiesSettings(transformers = listOf(
                            SessionCookieTransformerDigest()
                    ))
                }
            }

            application.routing {
                get("/1") {
                    call.session(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.sessionOrNull<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionId)}")
            }.let { response ->
                assertEquals("ok, id2", response.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "SESSION=$sessionId")
            }.let { response ->
                assertEquals("ok, null", response.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(brokenSession)}")
            }.let { response ->
                assertEquals("ok, null", response.response.content)
            }
        }
    }

    @Test
    fun testMacSession() {
        val key = hex("03515606058610610561058")
        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieByValue {
                    settings = SessionCookiesSettings(transformers = listOf(
                            SessionCookieTransformerMessageAuthentication(key)
                    ))
                }
            }

            application.routing {
                get("/1") {
                    call.session(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.sessionOrNull<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionId)}")
            }.let { response ->
                assertEquals("ok, id2", response.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "SESSION=$sessionId")
            }.let { response ->
                assertEquals("ok, null", response.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(brokenSession)}")
            }.let { response ->
                assertEquals("ok, null", response.response.content)
            }
        }
    }

    @Test
    fun testRoutes() {
        withTestApplication {
            application.routing {
                route("/") {
                    withSessions<TestUserSession> {
                        withCookieByValue()
                    }

                    get("/0") {
                        call.respondText("No session")
                    }
                    get("/1") {
                        var session: TestUserSession? = call.sessionOrNull()
                        assertNull(session)

                        assertFailsWith(IllegalArgumentException::class) {
                            call.session<TestUserSession>() // no no-arg constructor
                        }
                        assertFailsWith(IllegalArgumentException::class) {
                            call.session<EmptySession>() // bad class
                        }

                        call.session(TestUserSession("id1", emptyList()))
                        session = call.session()
                        assertNotNull(session)

                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(TestUserSession("id1", emptyList()), call.session<TestUserSession>())

                        call.respondText("ok, ${call.session<TestUserSession>().userId}")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies["SESSION"], "There should be no session set by default")
                assertEquals("No session", call.response.content)
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
