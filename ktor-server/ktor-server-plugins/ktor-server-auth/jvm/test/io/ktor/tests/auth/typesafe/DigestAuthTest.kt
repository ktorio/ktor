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
        algorithm.toDigester().digest(data.toByteArray(Charsets.ISO_8859_1))

    private fun String.normalize() = trimIndent().replace("\n", " ")

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
            header(
                HttpHeaders.Authorization,
                """
                    Digest
                    username="Mufasa",
                    realm="testrealm@host.com",
                    nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                    uri="/dir/index.html",
                    qop=auth,
                    nc=00000001,
                    cnonce="0a4f113b",
                    response="6629fae49393a05397450978507c4ef1",
                    opaque="5ccc069c403ebaf9f0171e9517f40e41"
                """.normalize()
            )
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
}
