/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.random.*
import kotlin.test.*

@Suppress("ReplaceSingleLineLet", "DEPRECATION")
class SessionTestJvm {
    private val cookieName = "_S" + Random.nextInt(100)

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

    private fun flipLastHexDigit(sessionId: String) = sessionId.mapIndexed { index, letter ->
        when {
            index != sessionId.lastIndex -> letter
            letter == '0' -> '1'
            else -> '0'
        }
    }.joinToString("")
}
