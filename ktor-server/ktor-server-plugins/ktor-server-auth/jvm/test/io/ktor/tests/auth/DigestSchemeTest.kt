/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalKtorApi::class)
class DigestSchemeTest {

    private fun digest(algorithm: DigestAlgorithm, data: String): ByteArray =
        algorithm.toDigester().digest(data.toByteArray(Charsets.UTF_8))

    private fun String.normalize() = trimIndent().replace("\n", " ")

    @Test
    fun `digest scheme returns typed principal`() = testApplication {
        val scheme = digest<TestUser>(name = "test-digest") {
            realm = "testrealm@host.com"
            nonceManager = GenerateOnlyNonceManager
            digestProvider { userName, realm, algorithm ->
                digest(algorithm, "$userName:$realm:Circle Of Life")
            }
            validate { credential ->
                TestUser(credential.userName, "${credential.userName}@test.com")
            }

            assertEquals(listOf(DigestAlgorithm.SHA_512_256), algorithms)
        }
        routing {
            authenticateWith(scheme) {
                get("/") { call.respondText("${call.principal.name}:${call.principal.email}") }
            }
        }

        val authHeader = """
            Digest
            username="Mufasa",
            realm="testrealm@host.com",
            nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
            uri="/",
            algorithm=SHA-512-256,
            qop=auth,
            nc=00000001,
            cnonce="0a4f113b",
            response="75fbc208f54fd80144783f654a6b207792870144cf31253e085516dc40c4eb8c",
            opaque="5ccc069c403ebaf9f0171e9517f40e41"
        """.normalize()
        val response = client.get("/") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Mufasa:Mufasa@test.com", response.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/").status)
    }
}
