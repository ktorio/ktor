package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
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
                addHeader(HttpHeaders.Cookie, "SESSION=${sessionParam.encodeURL()}")
            }.let { response ->
                assertEquals("ok, id1", response.response.content)
            }
        }
    }

    @Test
    fun testSessionById() {
        val sessionStorage = inMemorySessionStorage()

        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieBySessionId(sessionStorage)
            }

            application.routing {
                get("/0") {
                    response.sendText("It should be no session started")
                }
                get("/1") {
                    session(TestUserSession("id2", listOf("item1")))
                    response.sendText("ok")
                }
                get("/2") {
                    val session = session<TestUserSession>()
                    assertEquals("id2", session.userId)
                    assertEquals(listOf("item1"), session.cart)

                    response.sendText("ok")
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { response ->
                assertNull(response.response.cookies["SESSION_ID"], "It should be no session set by default")
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies["SESSION_ID"]
                assertNotNull(sessionCookie, "No session id cookie found")
                sessionId = sessionCookie!!.value
                assertTrue { sessionId.matches("[A-Za-z0-9]+".toRegex()) }
            }
            val serializedSession = sessionStorage.read(sessionId) { it.reader().readText() }.get()
            assertNotNull(serializedSession)
            assertEquals("id2", autoSerializerOf<TestUserSession>().deserialize(serializedSession).userId)

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION_ID=$sessionId")
            }
        }
    }

    @Test
    fun testDigestSession() {
        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieByValue {
                    settings = CookiesSettings(transformers = listOf(
                            DigestCookieTransformer()
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
                addHeader(HttpHeaders.Cookie, "SESSION=${sessionId.encodeURL()}")
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
                addHeader(HttpHeaders.Cookie, "SESSION=${brokenSession.encodeURL()}")
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
                    settings = CookiesSettings(transformers = listOf(
                            MessageAuthenticationCookieTransformer(key)
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
                addHeader(HttpHeaders.Cookie, "SESSION=${sessionId.encodeURL()}")
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
                addHeader(HttpHeaders.Cookie, "SESSION=${brokenSession.encodeURL()}")
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
                addHeader(HttpHeaders.Cookie, "SESSION=${sessionParam.encodeURL()}")
            }.let { response ->
                assertEquals("ok, id1", response.response.content)
            }
        }
    }
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
