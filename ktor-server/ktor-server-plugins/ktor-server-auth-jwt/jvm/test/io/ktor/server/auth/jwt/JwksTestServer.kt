/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import java.security.*
import java.security.interfaces.*
import java.util.*

/**
 * An RSA key pair that signs tokens and is published as a JWKS by [withJwksServer].
 */
internal class JwksTestKeys {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val algorithm = Algorithm.RSA256(publicKey, keyPair.private as RSAPrivateKey)

    fun token(issuer: String, audience: String, subject: String = "user"): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(subject)
        .withKeyId(KEY_ID)
        .sign(algorithm)

    fun jwksJson(): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val modulus = encoder.encodeToString(
            publicKey.modulus.toByteArray().dropWhile {
                it == 0.toByte()
            }.toByteArray()
        )
        val exponent = encoder.encodeToString(publicKey.publicExponent.toByteArray())
        return """{"keys":[{"kty":"RSA","alg":"RS256","use":"sig","kid":"$KEY_ID","n":"$modulus","e":"$exponent"}]}"""
    }

    private companion object {
        private const val KEY_ID = "test-jwks-key"
    }
}

/**
 * Serves [keys] at `/.well-known/jwks.json` over real HTTP, so that verifiers which build their own
 * `UrlJwkProvider` from an issuer URL can be tested. The [block] receives the issuer URL of the server.
 */
internal fun withJwksServer(
    keys: JwksTestKeys,
    block: suspend (issuer: String) -> Unit,
) = runTest {
    val port = ServerSocket(0).use { it.localPort }
    val server = embeddedServer(CIO, port = port) {
        routing {
            get("/.well-known/jwks.json") {
                call.respondText(keys.jwksJson(), ContentType.Application.Json)
            }
        }
    }.start(wait = false)

    try {
        block("http://127.0.0.1:$port/")
    } finally {
        server.stopSuspend(gracePeriodMillis = 10, timeoutMillis = 10)
    }
}
