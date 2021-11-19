/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.random.*
import kotlin.test.*

class SessionTest : TestWithKtor() {
    private val cookieName = "_S" + Random.nextInt(100)

    data class TestUserSession(val userId: String, val cart: List<String>)

    override val server = embeddedServer(Netty, serverPort) {
        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                cookie.encoding = CookieEncoding.BASE64_ENCODING
            }
        }
        routing {
            get {
                val session = call.sessions.get<TestUserSession>()
                assertNotNull(session)
                call.respond(session.userId)
            }

            get("login") {
                val session = TestUserSession("id1", emptyList())
                call.sessions.set(session)
                call.respond("Ok")
            }
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testSessionIsValid() {
        val client = HttpClient()

        runBlocking {
            var sessionParam = "cart=%23cl&userId=%23sid1"

            client.prepareGet("$testUrl/login").execute() {
                val sessionCookie = client.cookies(testUrl)[0]
                //sessionParam = sessionCookie.value
                //assertEquals(CookieEncoding.BASE64_ENCODING, sessionCookie.encoding)
            }

            client.prepareGet("$testUrl/") {
                header(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLQueryComponent()}")
            }.execute() { response ->
                assertEquals("test", response.content.readRemaining().readText())
            }
        }
    }
}
