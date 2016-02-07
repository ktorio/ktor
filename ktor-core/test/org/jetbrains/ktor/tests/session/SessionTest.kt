package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
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
                    response.sendText("It should be no session started")
                }
                get("/1") {
                    var session: TestUserSession? = sessionOrNull()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        session<TestUserSession>() // no no-arg constructor
                    }
                    assertFailsWith(IllegalArgumentException::class) {
                        session<EmptySession>() // bad class
                    }

                    session(TestUserSession("id1", emptyList()))
                    session = session()
                    assertNotNull(session)

                    response.sendText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), session<TestUserSession>())

                    response.sendText("ok, ${session<TestUserSession>().userId}")
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
                    session(TestUserSession("id2", emptyList()))
                    response.sendText("ok")
                }
                get("/2") {
                    response.sendText("ok, ${sessionOrNull<TestUserSession>()?.userId}")
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
                    session(TestUserSession("id2", emptyList()))
                    response.sendText("ok")
                }
                get("/2") {
                    response.sendText("ok, ${sessionOrNull<TestUserSession>()?.userId}")
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
                        response.sendText("It should be no session started")
                    }
                    get("/1") {
                        var session: TestUserSession? = sessionOrNull()
                        assertNull(session)

                        assertFailsWith(IllegalArgumentException::class) {
                            session<TestUserSession>() // no no-arg constructor
                        }
                        assertFailsWith(IllegalArgumentException::class) {
                            session<EmptySession>() // bad class
                        }

                        session(TestUserSession("id1", emptyList()))
                        session = session()
                        assertNotNull(session)

                        response.sendText("ok")
                    }
                    get("/2") {
                        assertEquals(TestUserSession("id1", emptyList()), session<TestUserSession>())

                        response.sendText("ok, ${session<TestUserSession>().userId}")
                    }
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
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
