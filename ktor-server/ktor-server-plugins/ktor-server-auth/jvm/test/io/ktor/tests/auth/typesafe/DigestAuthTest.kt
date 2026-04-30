/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.testing.*
import io.ktor.util.GenerateOnlyNonceManager
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestAuthTest {

    private fun digest(algorithm: DigestAlgorithm, data: String): ByteArray =
        algorithm.toDigester().digest(data.toByteArray(Charsets.UTF_8))

    private fun String.normalize() = trimIndent().replace("\n", " ")

    private fun digestAuthHeader(uri: String, response: String): String = """
        Digest
        username="Mufasa",
        realm="testrealm@host.com",
        nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
        uri="$uri",
        qop=auth,
        nc=00000001,
        cnonce="0a4f113b",
        response="$response",
        opaque="5ccc069c403ebaf9f0171e9517f40e41"
    """.normalize()

    private fun createDigestScheme(name: String) = digest<TestUser>(name) {
        realm = "testrealm@host.com"
        nonceManager = GenerateOnlyNonceManager
        digestProvider { userName, realm, algorithm ->
            digest(algorithm, "$userName:$realm:Circle Of Life")
        }
        validate { credential ->
            TestUser(credential.userName, "${credential.userName}@test.com")
        }
    }

    @Test
    fun `digest scheme returns typed principal`() = testApplication {
        routing {
            authenticateWith(createDigestScheme("test-digest")) {
                get("/") { call.respondText("${principal.name}:${principal.email}") }
            }
        }

        val response = client.get("/") {
            header(HttpHeaders.Authorization, digestAuthHeader("/", "d44a9a5b1ac4e32c0587816674183be6"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Mufasa:Mufasa@test.com", response.bodyAsText())
    }

    @Test
    fun `digest scheme rejects missing credentials`() = testApplication {
        routing {
            authenticateWith(createDigestScheme("test-digest-reject")) {
                get("/") { call.respondText(principal.name) }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/").status)
    }

    @Test
    fun `last digest provider overload wins`() = testApplication {
        @Suppress("DEPRECATION")
        val md5 = DigestAlgorithm.MD5

        fun badDigest(algorithm: DigestAlgorithm) = digest(algorithm, "bad")

        val legacyThenV2 = digest<TestUser>("digest-legacy-then-v2") {
            realm = "testrealm@host.com"
            nonceManager = GenerateOnlyNonceManager
            digestProvider { _, _ -> badDigest(md5) }
            digestProvider { userName, realm, algorithm -> digest(algorithm, "$userName:$realm:Circle Of Life") }
            validate { credential -> TestUser(credential.userName, "${credential.userName}@test.com") }
        }

        val v2ThenLegacy = digest<TestUser>("digest-v2-then-legacy") {
            realm = "testrealm@host.com"
            nonceManager = GenerateOnlyNonceManager
            digestProvider { _, _, algorithm -> badDigest(algorithm) }
            digestProvider { userName, realm -> digest(md5, "$userName:$realm:Circle Of Life") }
            validate { credential -> TestUser(credential.userName, "${credential.userName}@test.com") }
        }

        routing {
            authenticateWith(legacyThenV2) {
                get("/v2") { call.respondText(principal.name) }
            }
            authenticateWith(v2ThenLegacy) {
                get("/legacy") { call.respondText(principal.name) }
            }
        }

        val v2Response = client.get("/v2") {
            header(HttpHeaders.Authorization, digestAuthHeader("/v2", "76c83205d831e6638b673f66d4bbe8c8"))
        }
        assertEquals(HttpStatusCode.OK, v2Response.status)

        val legacyResponse = client.get("/legacy") {
            header(HttpHeaders.Authorization, digestAuthHeader("/legacy", "8a6de65c4d8371477d9b057f111f3dbf"))
        }
        assertEquals(HttpStatusCode.OK, legacyResponse.status)
    }
}
