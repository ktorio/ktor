/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.random.*
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("ReplaceSingleLineLet", "DEPRECATION")
class SessionTest {
    private val cookieName = "_S" + Random.nextInt(100)

    @Test
    fun testSessionCreateDelete() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName)
            }

            application.routing {
                get("/0") {
                    call.sessions.set(TestUserSession("a", emptyList()))
                    call.sessions.clear<TestUserSession>()
                    call.respondText("No session")
                }
            }
            assertNull(
                handleRequest(HttpMethod.Get, "/0").response.cookies[cookieName],
                "There should be no session data after setting and clearing"
            )
        }
    }

    @Test
    fun testSessionByValue() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    cookie.domain = "foo.bar"
                    cookie.maxAge = 1.hours
                }
            }

            application.routing {
                get("/0") {
                    assertNull(call.sessions.get<TestUserSession>())
                    call.respondText("No session")
                }
                get("/1") {
                    var session: TestUserSession? = call.sessions.get()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        call.sessions.set(EmptySession()) // bad class
                    }

                    call.sessions.set(TestUserSession("id1", emptyList()))
                    session = call.sessions.get()
                    assertNotNull(session)

                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), call.sessions.get<TestUserSession>())

                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            assertNull(
                handleRequest(HttpMethod.Get, "/0").response.cookies[cookieName],
                "There should be no session set by default"
            )

            var sessionParam: String
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie.value
                assertEquals("foo.bar", sessionCookie.domain)
                assertEquals(3600, sessionCookie.maxAge)
                assertNotNull(sessionCookie.expires)

                assertEquals(
                    TestUserSession("id1", emptyList()),
                    defaultSessionSerializer<TestUserSession>().deserialize(sessionParam)
                )
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLQueryComponent()}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }

    @Test
    fun testSessionWithEncodedCookie() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, SessionStorageMemory()) {
                    cookie.encoding = CookieEncoding.BASE64_ENCODING
                }
            }
            application.routing {
                get("/1") {
                    var session: TestUserSession? = call.sessions.get()
                    assertNull(session)

                    session = TestUserSession("id1", emptyList())
                    call.sessions.set(session)

                    session = call.sessions.get()
                    assertNotNull(session)

                    call.respond("Ok")
                }
                get("/2") {
                    val session = call.sessions.get<TestUserSession>()
                    assertNotNull(session)
                    call.respond(session.userId)
                }
            }
            var sessionParam: String
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]

                assertNotNull(sessionCookie, "No session cookie found")
                assertEquals(CookieEncoding.BASE64_ENCODING, sessionCookie.encoding)

                sessionParam = sessionCookie.value.encodeBase64()
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionParam")
            }.let { call ->
                assertEquals("id1", call.response.content)
            }
        }
    }

    @Test
    fun testSessionByValueMac() {
        val key = hex("03515606058610610561058")
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerMessageAuthentication(key))
                }
            }

            commonSignedChecks()
        }
    }

    @Test
    fun testSessionEncrypted() {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerEncrypt(encryptKey, signKey, { forcedIvForTesting }))
                }
            }

            application.routing {
                get("/3") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/4") {
                    call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
                }
            }

            handleRequest(HttpMethod.Get, "/3").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertEquals(
                    "00112233445566778899aabbccddeeff/" +
                        "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                        "51a5e9fcd1c91418f9a623bafa5022a524348e44244265dc0cab2cebacc28a5d",
                    sessionCookie!!.value
                )
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(HttpHeaders.Cookie, "$cookieName=INVALID")
            }.let { call ->
                assertEquals("ok:null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(HttpHeaders.Cookie, "$cookieName=abc/abc:abc")
            }.let { call ->
                assertEquals("ok:null", call.response.content)
            }

            commonSignedChecks()
        }
    }

    private fun TestApplicationEngine.commonSignedChecks() {
        application.routing {
            get("/1") {
                call.sessions.set(TestUserSession("id2", emptyList()))
                call.respondText("ok")
            }
            get("/2") {
                call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
            }
        }

        var sessionId: String
        handleRequest(HttpMethod.Get, "/1").let { call ->
            val sessionCookie = call.response.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionId = sessionCookie.value
        }

        handleRequest(HttpMethod.Get, "/2") {
            addHeader(HttpHeaders.Cookie, "$cookieName=${sessionId.encodeURLQueryComponent()}")
        }.let { call ->
            assertEquals("ok, id2", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
        }.let { call ->
            assertEquals("ok, null", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            val brokenSession = flipLastHexDigit(sessionId)
            addHeader(HttpHeaders.Cookie, "$cookieName=${brokenSession.encodeURLQueryComponent()}")
        }.let { call ->
            assertEquals("ok, null", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            val invalidHex = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
            addHeader(HttpHeaders.Cookie, "$cookieName=${invalidHex.encodeURLQueryComponent()}")
        }.let { call ->
            assertEquals("ok, null", call.response.content)
        }
    }

    @Test
    fun testRoutes() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName)
            }
            application.routing {
                route("/") {
                    get("/0") {
                        assertNull(call.sessions.get<TestUserSession>())
                        call.respondText("No session")
                    }
                    get("/1") {
                        var session: TestUserSession? = call.sessions.get()
                        assertNull(session)

                        assertFailsWith(IllegalArgumentException::class) {
                            call.sessions.set(EmptySession()) // bad class
                        }

                        call.sessions.set(TestUserSession("id1", emptyList()))
                        session = call.sessions.get()
                        assertNotNull(session)

                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(TestUserSession("id1", emptyList()), call.sessions.get<TestUserSession>())

                        call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies[cookieName], "There should be no session set by default")
                assertEquals("No session", call.response.content)
            }

            var sessionParam: String
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie.value

                assertEquals(
                    TestUserSession("id1", emptyList()),
                    defaultSessionSerializer<TestUserSession>().deserialize(sessionParam)
                )
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLQueryComponent()}")
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
                        call.sessions.set(sessionA)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionA, call.sessions.get<TestUserSession>())
                        call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                    }
                }

                route("/b") {
                    install(Sessions) {
                        cookie<TestUserSessionB>(cookieName)
                    }
                    get("/1") {
                        call.sessions.set(sessionB)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionB, call.sessions.get<TestUserSessionB>())
                        call.respondText("ok, ${call.sessions.get<TestUserSessionB>()?.userId}")
                    }
                }
            }

            var sessionParam: String
            handleRequest(HttpMethod.Get, "/a/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie.value

                assertEquals(sessionA, defaultSessionSerializer<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/a/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLQueryComponent()}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/b/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie.value

                assertEquals(sessionB, defaultSessionSerializer<TestUserSessionB>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/b/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLQueryComponent()}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }
        }
    }

    @Test
    fun testSessionById() {
        val sessionStorage = SessionStorageMemory()

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }

            application.routing {
                get("/0") {
                    call.respondText("There should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                }
                get("/2") {
                    val session = call.sessions.get<TestUserSession>()
                    assertEquals("id2", session?.userId)
                    assertEquals(listOf("item1"), session?.cart)

                    call.respondText("ok")
                }
                get("/3") {
                    call.respondText(call.sessions.get<TestUserSession>()?.userId ?: "no session")
                }
            }

            assertNull(
                handleRequest(HttpMethod.Get, "/0").response.cookies[cookieName],
                "There should be no session set by default"
            )

            var sessionId: String
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                sessionId = sessionCookie.value
                assertTrue { sessionId.matches("[A-Za-z0-9]+".toRegex()) }
            }
            val serializedSession = runBlocking {
                sessionStorage.read(sessionId)
            }
            assertNotNull(serializedSession)
            assertEquals("id2", defaultSessionSerializer<TestUserSession>().deserialize(serializedSession).userId)

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }

            handleRequest(HttpMethod.Get, "/3") {
                addHeader(HttpHeaders.Cookie, "$cookieName=bad$sessionId")
            }.let { call ->
                assertEquals("no session", call.response.content)
            }
        }
    }

    @Test
    fun testSessionByIdAccessors() {
        val sessionStorage = SessionStorageMemory()

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }

            application.routing {
                get("/0") {
                    assertNull(call.sessionId, "There should be no session set by default")
                    assertNull(call.sessionId<TestUserSession>(), "There should be no session set by default")
                    assertFails {
                        call.sessionId<EmptySession>()
                    }
                    call.respondText("There should be no session started")
                }
            }

            assertEquals("There should be no session started", handleRequest(HttpMethod.Get, "/0").response.content)
        }
    }

    @Test
    fun testSessionByIdServer() {
        val sessionStorage = SessionStorageMemory()
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }
            var serverSessionId = "_invalid"
            application.routing {
                get("/0") {
                    assertNull(call.sessionId, "There should be no session set by default")
                    assertNull(call.sessionId<TestUserSession>(), "There should be no session set by default")
                    call.respondText("There should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                    serverSessionId = call.sessionId ?: error("No session id found.")
                    assertTrue { serverSessionId.matches("[A-Za-z0-9]+".toRegex()) }
                }
            }

            assertNull(
                handleRequest(HttpMethod.Get, "/0").response.cookies[cookieName],
                "There should be no session set by default"
            )

            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                val clientSessionId = sessionCookie.value
                assertEquals(serverSessionId, clientSessionId)
            }

            val serializedSession = runBlocking {
                sessionStorage.read(serverSessionId)
            }
            assertNotNull(serializedSession)
            assertEquals("id2", defaultSessionSerializer<TestUserSession>().deserialize(serializedSession).userId)
        }
    }

    @Test
    fun testSessionByIdCookie() {
        val sessionStorage = SessionStorageMemory()
        var id = 777
        val durationSeconds = 5L

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage) {
                    cookie.maxAge = durationSeconds.seconds
                    identity { (id++).toString() }
                }
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
            }

            fun GMTDate.plusAndDiscardMillis() = (this + durationSeconds * 1000L).toHttpDate().fromHttpToGmtDate()

            val before = GMTDate()
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                val after = GMTDate()

                assertEquals(durationSeconds, sessionCookie.maxAge.toLong())
                assertEquals("777", sessionCookie.value)
                assertNotNull(sessionCookie.expires, "Expires cookie value is not set")
                assertTrue("Expires cookie parameter value should be in the specified dates range") {
                    sessionCookie.expires!! in before.plusAndDiscardMillis()..after.plusAndDiscardMillis()
                }
            }
        }
    }

    @Test
    fun testSessionByInvalidId() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, SessionStorageMemory())
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                }
                get("/2") {
                    val session = call.sessions.get<TestUserSession>()
                    call.respondText(session?.userId ?: "none")
                }
            }

            val call = handleRequest {
                uri = "/1"
                addHeader(HttpHeaders.Cookie, "$cookieName=invalid")
            }
            val sessionId = call.response.cookies[cookieName]!!.value

            val nextCall = handleRequest {
                uri = "/1"
                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }
            assertEquals(sessionId, nextCall.response.cookies[cookieName]!!.value)

            handleRequest {
                uri = "/2"
                addHeader(HttpHeaders.Cookie, "$cookieName=invalid2")
            }.let { call2 ->
                // we are sending expired cookie to remove outdated/invalid session id
                assertEquals("none", call2.response.content)
                call2.response.cookies[cookieName].let { cookie ->
                    assertNotNull(cookie, "cookie should be resend (expired)")
                    assertEquals(0, cookie.maxAge)
                    assertNotNull(cookie.expires)
                    assertEquals(1970, cookie.expires!!.year)
                }
            }
        }
    }

    @Test
    fun testHttpSessionCookie(): Unit = withTestApplication {
        // test session cookie in terms of HTTP
        // that should be discarded on client exit

        application.install(Sessions) {
            cookie<TestUserSession>(cookieName, SessionStorageMemory()) {
                cookie.maxAge = null
            }
        }

        application.routing {
            get("/set-cookie") {
                call.sessions.set(TestUserSession("id2", listOf("item1")))
                call.respondText("ok")
            }
        }

        handleRequest(HttpMethod.Get, "/set-cookie").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val parsedCookies = call.response.cookies[cookieName]!!
            assertNull(parsedCookies.expires)
            assertEquals(0, parsedCookies.maxAge)
        }
    }

    @Test
    fun settingSessionAfterResponseTest(): Unit = withTestApplication {
        application.install(Sessions) {
            cookie<TestUserSession>(cookieName)
        }

        application.routing {
            get("/after-response") {
                call.respondText("OK")
                call.sessions.set(TestUserSession("id", emptyList()))
            }
        }

        assertFailsWith<TooLateSessionSetException> {
            runBlocking {
                handleRequest(HttpMethod.Get, "/after-response").response.content
            }
        }
    }

    @Test
    fun testSessionLongDuration(): Unit = withTestApplication {
        val transport = SessionTransportCookie(
            "test",
            CookieConfiguration().apply {
                maxAge = (365 * 100).days
            },
            emptyList()
        )

        val call = createCall {}
        transport.send(call, "my-session")

        val cookies = call.response.cookies["test"]
        assertNotNull(cookies)
        assertEquals(Int.MAX_VALUE, cookies.maxAge)
    }

    @Test
    fun testSessionOverflowDuration(): Unit = withTestApplication {
        val transport = SessionTransportCookie(
            "test",
            CookieConfiguration().apply {
                maxAge = Long.MAX_VALUE.seconds
            },
            emptyList()
        )

        val call = createCall {}
        transport.send(call, "my-session")

        val cookies = call.response.cookies["test"]
        assertNotNull(cookies)
        assertEquals(Int.MAX_VALUE, cookies.maxAge)
    }

    @Test
    fun testDuplicateProvidersDiagnostics(): Unit = withTestApplication {
        application.install(Sessions) {
            cookie<TestUserSession>("name1")

            assertFails("Registering the same provider twice should be prohibited") {
                cookie<TestUserSession>("name1")
            }

            assertFails("Registering provider with the same name should be prohibited") {
                cookie<TestUserSessionB>("name1")
            }

            assertFails("Registering provider with the same type should be prohibited") {
                cookie<TestUserSession>("name2")
            }

            on("Registering another provider should be allowed") {
                cookie<TestUserSessionB>("name2")
            }
        }
    }

    @Test
    fun testMissingSessionsPlugin(): Unit = withTestApplication {
        application.routing {
            get("/") {
                val cause = assertFailsWith<MissingApplicationPluginException> {
                    call.sessions.get<EmptySession>()
                }
                call.respondText(cause.key.name)
            }
        }
        assertEquals(Sessions.key.name, handleRequest(HttpMethod.Get, "/").response.content)
    }

    data class Token(val secret: Int)

    @Test
    fun secureCookie() = testApplication {
        install(Sessions) {
            cookie<Token>("SESSION") {
                cookie.path = "/token"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "strict"
                cookie.maxAgeInSeconds = 1.days.inWholeSeconds
                cookie.secure = true
            }
        }
        routing {
            get("/token") {
                call.sessions.set(Token(42))
                call.respond(HttpStatusCode.OK)
            }
        }

        client.get("https://localhost/token").body<Unit>()
    }

    @Test
    fun testMissingSession(): Unit = withTestApplication {
        application.intercept(ApplicationCallPipeline.Monitoring) {
            assertFailsWith<SessionNotYetConfiguredException> {
                call.sessions.get<EmptySession>()
            }
            call.respondText("OK")
            finish()
        }
        application.routing {
            get("/") {
            }
        }
        application.install(Sessions) {
            cookie<TestUserSession>("name1")
        }

        assertEquals("OK", handleRequest(HttpMethod.Get, "/").response.content)
    }

    private fun flipLastHexDigit(sessionId: String) = sessionId.mapIndexed { index, letter ->
        when {
            index != sessionId.lastIndex -> letter
            letter == '0' -> '1'
            else -> '0'
        }
    }.joinToString("")
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
data class TestUserSessionB(val userId: String, val cart: List<String>)
