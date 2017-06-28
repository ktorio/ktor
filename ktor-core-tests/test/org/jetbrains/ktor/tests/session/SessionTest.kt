package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.application.*
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
    fun testDefaultSessions() {
        withTestApplication {
            application.install(Sessions)
            val values = valuesOf("name" to listOf("value"))
            application.routing {
                get("/1") {
                    call.setSession(values)
                    call.respondText("OK")
                }
                get("/2") {
                    call.respondText(call.currentSessionOf<ValuesMap>().toString())
                }
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(values, autoSerializerOf<ValuesMap>().deserialize(sessionParam))
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ValuesMap(case=true) [name=[value]]", call.response.content)
            }
        }

    }

    @Test
    fun testSessionInlineValue() {
        withTestApplication {
            application.install(Sessions) {
                cookieByValue(TestUserSession::class)
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
                assertNull(call.response.cookies["SESSION"], "There should be no session set by default")
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
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
                cookieByValue(TestUserSession::class)
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
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "SESSION=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(brokenSession)}")
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
                cookieByValue(TestUserSession::class)
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
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "SESSION=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(brokenSession)}")
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
                        cookieByValue(TestUserSession::class)
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

    @Test
    fun testRoutesIsolation() {
        withTestApplication {
            val sessionA = TestUserSession("id1", listOf("a"))
            val sessionB = TestUserSessionB("id2", listOf("b"))
            application.routing {
                route("/a") {
                    install(Sessions) {
                        cookieByValue(TestUserSession::class)
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
                        cookieByValue(TestUserSessionB::class)
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
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionA, autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/a/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/b/1").let { call ->
                val sessionCookie = call.response.cookies["SESSION"]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionB, autoSerializerOf<TestUserSessionB>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/b/2") {
                addHeader(HttpHeaders.Cookie, "SESSION=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }
        }
    }
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
data class TestUserSessionB(val userId: String, val cart: List<String>)
