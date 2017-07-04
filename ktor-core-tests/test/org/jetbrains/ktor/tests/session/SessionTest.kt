package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.util.*
import kotlin.test.*

class SessionTest {
    val cookieName = "_S" + Random().nextInt(100)

    @Test
    fun testSessionInlineValue() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName)
            }

            application.routing {
                get("/0") {
                    assertNull(call.currentSession())
                    call.respondText("No session")
                }
                get("/1") {
                    var session: TestUserSession? = call.currentSessionOf()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        call.setSession(EmptySession()) // bad class
                    }

                    call.setSession(TestUserSession("id1", emptyList()))
                    session = call.currentSessionOf()
                    assertNotNull(session)

                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), call.currentSessionOf<TestUserSession>())

                    call.respondText("ok, ${call.currentSessionOf<TestUserSession>()?.userId}")
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies[cookieName], "There should be no session set by default")
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }

    @Test
    fun testDigestSession() {
        withTestApplication {
            application.install(Sessions) {
                transformers.add(SessionCookieTransformerDigest())
                cookie<TestUserSession>(cookieName)
            }

            application.routing {
                get("/1") {
                    call.setSession(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.currentSessionOf<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }
        }
    }

    @Test
    fun testMacSession() {
        val key = hex("03515606058610610561058")
        withTestApplication {
            application.install(Sessions) {
                transformers.add(SessionCookieTransformerMessageAuthentication(key))
                cookie<TestUserSession>(cookieName)
            }

            application.routing {
                get("/1") {
                    call.setSession(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.currentSessionOf<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }
        }
    }

    @Test
    fun testRoutes() {
        withTestApplication {
            application.routing {
                route("/") {
                    install(Sessions) {
                        cookie<TestUserSession>(cookieName)
                    }

                    get("/0") {
                        assertNull(call.currentSession())
                        call.respondText("No session")
                    }
                    get("/1") {
                        var session: TestUserSession? = call.currentSessionOf()
                        assertNull(session)

                        assertFailsWith(IllegalArgumentException::class) {
                            call.setSession(EmptySession()) // bad class
                        }

                        call.setSession(TestUserSession("id1", emptyList()))
                        session = call.currentSessionOf()
                        assertNotNull(session)

                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(TestUserSession("id1", emptyList()), call.currentSessionOf<TestUserSession>())

                        call.respondText("ok, ${call.currentSessionOf<TestUserSession>()?.userId}")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies[cookieName], "There should be no session set by default")
                assertEquals("No session", call.response.content)
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }

    @Test
    fun testRoutesIsolation() {
        withTestApplication {
            val sessionA = TestUserSession("id1", listOf("a"))
            val sessionB = TestUserSessionB("id2", listOf("b"))
            application.routing {
                route("/a") {
                    install(Sessions) {
                        cookie<TestUserSession>(cookieName)
                    }

                    get("/1") {
                        call.setSession(sessionA)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionA, call.currentSessionOf<TestUserSession>())
                        call.respondText("ok, ${call.currentSessionOf<TestUserSession>()?.userId}")
                    }
                }

                route("/b") {
                    install(Sessions) {
                        cookie<TestUserSessionB>(cookieName)
                    }
                    get("/1") {
                        call.setSession(sessionB)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionB, call.currentSessionOf<TestUserSessionB>())
                        call.respondText("ok, ${call.currentSessionOf<TestUserSessionB>()?.userId}")
                    }
                }
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/a/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionA, autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/a/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/b/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionB, autoSerializerOf<TestUserSessionB>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/b/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }
        }
    }
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
data class TestUserSessionB(val userId: String, val cart: List<String>)
