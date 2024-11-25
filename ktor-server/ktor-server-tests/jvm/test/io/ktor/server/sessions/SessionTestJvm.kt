/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.random.*
import kotlin.test.*

@Suppress("ComplexRedundantLet")
class SessionTestJvm {
    private val cookieName = "_S" + Random.nextInt(100)

    @Test
    fun testSessionByValueMac() = testApplication {
        val key = hex("03515606058610610561058")

        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                transform(SessionTransportTransformerMessageAuthentication(key))
            }
        }

        commonSignedChecks()
    }

    @Test
    fun testSessionEncrypted() = testApplication {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                transform(SessionTransportTransformerEncrypt(encryptKey, signKey, { forcedIvForTesting }))
            }
        }

        routing {
            get("/3") {
                call.sessions.set(TestUserSession("id2", emptyList()))
                call.respondText("ok")
            }
            get("/4") {
                call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
            }
        }

        commonSignedChecks()

        client.get("/3").let { response ->
            val sessionCookie = response.setCookie().find { it.name == cookieName }
            assertEquals(
                "00112233445566778899aabbccddeeff/" +
                    "9b8cf9900ff0f63849cb7a2ad71ed8a1ac9f0e2735492d2a5129cf278d70ab27:" +
                    "40af9f393a00034b53d2267251047259005bdf5ff104eede599149e80e6fee13",
                sessionCookie!!.value
            )
        }

        client.get("/4") {
            header(HttpHeaders.Cookie, "$cookieName=INVALID")
        }.let { response ->
            assertEquals("ok:null", response.bodyAsText())
        }

        client.get("/4") {
            header(HttpHeaders.Cookie, "$cookieName=abc/abc:abc")
        }.let { response ->
            assertEquals("ok:null", response.bodyAsText())
        }
    }

    @Test
    fun testSessionEncryptedBackwardCompatible() = testApplication {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                serializer = reflectionSessionSerializer()
                transform(
                    SessionTransportTransformerEncrypt(
                        encryptKey,
                        signKey,
                        { forcedIvForTesting },
                        backwardCompatibleRead = true
                    )
                )
            }
        }

        routing {
            get("/3") {
                call.sessions.set(TestUserSession("id2", emptyList()))
                call.respondText("ok")
            }
            get("/4") {
                call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
            }
        }

        commonSignedChecks()

        client.get("/3").let { response ->
            val sessionCookie = response.setCookie().find { it.name == cookieName }
            assertEquals(
                "00112233445566778899aabbccddeeff/" +
                    "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                    "65f0bd15bfa7e96b8bd9e134e23a24860b324e361c606e6800af050f5d104b68",
                sessionCookie!!.value
            )
        }

        client.get("/4") {
            header(
                HttpHeaders.Cookie,
                "$cookieName=" + "00112233445566778899aabbccddeeff/" +
                    "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                    "51a5e9fcd1c91418f9a623bafa5022a524348e44244265dc0cab2cebacc28a5d"
            )
        }.let { response ->
            assertEquals("ok:id2", response.bodyAsText())
        }
    }

    @Test
    fun testSessionEncryptedNotBackwardCompatible() = testApplication {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                serializer = reflectionSessionSerializer()
                transform(
                    SessionTransportTransformerEncrypt(
                        encryptKey,
                        signKey,
                        { forcedIvForTesting },
                    )
                )
            }
        }

        routing {
            get("/3") {
                call.sessions.set(TestUserSession("id2", emptyList()))
                call.respondText("ok")
            }
            get("/4") {
                call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
            }
        }

        commonSignedChecks()

        client.get("/3").let { response ->
            val sessionCookie = response.setCookie().find { it.name == cookieName }
            assertEquals(
                "00112233445566778899aabbccddeeff/" +
                    "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                    "65f0bd15bfa7e96b8bd9e134e23a24860b324e361c606e6800af050f5d104b68",
                sessionCookie!!.value
            )
        }

        client.get("/4") {
            header(
                HttpHeaders.Cookie,
                "$cookieName=" + "00112233445566778899aabbccddeeff/" +
                    "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                    "51a5e9fcd1c91418f9a623bafa5022a524348e44244265dc0cab2cebacc28a5d"
            )
        }.let { response ->
            assertEquals("ok:null", response.bodyAsText())
        }
    }

    private suspend fun ApplicationTestBuilder.commonSignedChecks() {
        routing {
            get("/1") {
                call.sessions.set(TestUserSession("id 2", emptyList()))
                call.respondText("ok")
            }
            get("/2") {
                call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
            }
        }

        var sessionId: String
        var sessionHeader: String
        client.get("/1").let { response ->
            val sessionCookie = response.setCookie().find { it.name == cookieName }
            assertNotNull(sessionCookie, "No session cookie found")
            sessionId = sessionCookie.value
            sessionHeader = response.headers[HttpHeaders.SetCookie]!!
        }

        client.get("/2") {
            header(HttpHeaders.Cookie, "$cookieName=${sessionId.encodeURLParameter()}")
        }.let { response ->
            assertEquals("ok, id 2", response.bodyAsText())
        }

        client.get("/2") {
            header(HttpHeaders.Cookie, sessionHeader)
        }.let { response ->
            assertEquals("ok, id 2", response.bodyAsText())
        }

        client.get("/2").let { response ->
            assertEquals("ok, null", response.bodyAsText())
        }

        client.get("/2") {
            val brokenSession = flipLastHexDigit(sessionId)
            header(HttpHeaders.Cookie, "$cookieName=${brokenSession.encodeURLParameter()}")
        }.let { response ->
            assertEquals("ok, null", response.bodyAsText())
        }

        client.get("/2") {
            val invalidHex = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
            header(HttpHeaders.Cookie, "$cookieName=${invalidHex.encodeURLParameter()}")
        }.let { response ->
            assertEquals("ok, null", response.bodyAsText())
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
