/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc.utils

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.auth.*
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

internal class OpenIdTestKeys(
    private val keyId: String = "kid-1",
    jwkAlgorithm: SignatureAlgorithm? = SignatureAlgorithm.RSA_SHA_256,
) {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    val jwk: Jwk = Jwk.fromValues(rsaJwkValues(publicKey, keyId, jwkAlgorithm))

    val jwkProvider: JwkProvider = JwkProvider { requestedKeyId ->
        require(requestedKeyId == null || requestedKeyId == keyId) { "Unexpected key id $requestedKeyId" }
        jwk
    }

    fun token(
        audience: String,
        subject: String? = "ktor-user",
        issuer: String = ISSUER_URL,
        keyId: String? = this.keyId,
        nonce: String? = null,
        name: String? = null,
        email: String? = null,
        clientId: String? = null,
        expiresAt: Date? = null,
        atHash: String? = null,
        algorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA_256,
        headerClaims: Map<String, Any> = emptyMap(),
    ): String {
        val builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)

        if (headerClaims.isNotEmpty()) {
            builder.withHeader(headerClaims)
        }
        subject?.let { builder.withSubject(it) }
        keyId?.let { builder.withKeyId(it) }
        nonce?.let { builder.withClaim("nonce", it) }
        name?.let { builder.withClaim("name", it) }
        email?.let { builder.withClaim("email", it) }
        clientId?.let { builder.withClaim("client_id", it) }
        expiresAt?.let { builder.withExpiresAt(it) }
        atHash?.let { builder.withClaim("at_hash", it) }

        return builder.sign(signingAlgorithm(algorithm))
    }

    fun hmacToken(
        audience: String,
        subject: String = "ktor-user",
        issuer: String = ISSUER_URL,
    ): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(subject)
        .sign(Algorithm.HMAC256("secret"))

    fun unsignedToken(
        audience: String,
        subject: String = "ktor-user",
        issuer: String = ISSUER_URL,
    ): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(subject)
        .sign(Algorithm.none())

    private fun signingAlgorithm(algorithm: SignatureAlgorithm): Algorithm =
        when (algorithm) {
            SignatureAlgorithm.RSA_SHA_256 -> Algorithm.RSA256(publicKey, privateKey)
            SignatureAlgorithm.RSA_SHA_384 -> Algorithm.RSA384(publicKey, privateKey)
            SignatureAlgorithm.RSA_SHA_512 -> Algorithm.RSA512(publicKey, privateKey)
            else -> error("Unsupported test signing algorithm: ${algorithm.jwaName ?: algorithm.name}")
        }
}

internal val SignatureAlgorithm.testJwaName: String
    get() = checkNotNull(jwaName) { "Test signing algorithm $name has no JWA name" }

internal fun testAtHash(
    accessToken: String,
    algorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA_256,
): String {
    val digest = algorithm.digestAlgorithm.toDigester().digest(accessToken.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOfRange(0, digest.size / 2))
}

internal fun jwkProviderWithMultipleKeys(vararg keys: OpenIdTestKeys): JwkProvider =
    JwkProvider { requestedKeyId ->
        if (requestedKeyId == null) {
            throw SigningKeyNotFoundException("Multiple keys are available", null)
        }
        keys.firstOrNull { it.jwk.id == requestedKeyId }?.jwk
            ?: throw SigningKeyNotFoundException("No key found for kid $requestedKeyId", null)
    }

internal fun jwkProviderWithoutVerifyOperation(keys: OpenIdTestKeys): JwkProvider {
    val values = keys.jwk.additionalAttributes.toMutableMap()
    values["kid"] = keys.jwk.id
    values["kty"] = keys.jwk.type
    values["alg"] = keys.jwk.algorithm
    values["use"] = keys.jwk.usage
    values["key_ops"] = listOf("sign")
    val jwk = Jwk.fromValues(values)
    return JwkProvider { requestedKeyId ->
        if (requestedKeyId == null || requestedKeyId == jwk.id) {
            jwk
        } else {
            throw SigningKeyNotFoundException("No key found for kid $requestedKeyId", null)
        }
    }
}

private fun rsaJwkValues(
    publicKey: RSAPublicKey,
    keyId: String,
    algorithm: SignatureAlgorithm? = SignatureAlgorithm.RSA_SHA_256,
): Map<String, Any> =
    mutableMapOf<String, Any>(
        "kid" to keyId,
        "kty" to "RSA",
        "use" to "sig",
        "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(
            publicKey.modulus.toByteArray().stripLeadingZero()
        ),
        "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(
            publicKey.publicExponent.toByteArray().stripLeadingZero()
        ),
    ).apply {
        algorithm?.let { put("alg", it.testJwaName) }
    }

private fun ByteArray.stripLeadingZero(): ByteArray =
    if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this
