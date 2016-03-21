package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class ServerSessionTest {

    @Test
    fun testSessionById() {
        val sessionStorage = inMemorySessionStorage()

        withTestApplication {
            application.withSessions<TestUserSession> {
                withCookieBySessionId(sessionStorage)
            }

            application.routing {
                get("/0") {
                    call.respondText("It should be no session started")
                }
                get("/1") {
                    call.session(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                }
                get("/2") {
                    val session = call.session<TestUserSession>()
                    assertEquals("id2", session.userId)
                    assertEquals(listOf("item1"), session.cart)

                    call.respondText("ok")
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
}
