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
import io.ktor.server.auth.oidc.*

internal fun hmacToken(
    audience: String,
    subject: String = "ktor-user",
    issuer: String = ISSUER_URL,
): String = JWT.create()
    .withIssuer(issuer)
    .withAudience(audience)
    .withSubject(subject)
    .sign(Algorithm.HMAC256("secret"))

internal fun unsignedToken(
    audience: String,
    subject: String = "ktor-user",
    issuer: String = ISSUER_URL,
): String = JWT.create()
    .withIssuer(issuer)
    .withAudience(audience)
    .withSubject(subject)
    .sign(Algorithm.none())

internal val SignatureAlgorithm.testJwaName: String
    get() = checkNotNull(jwaName) { "Test signing algorithm $name has no JWA name" }

internal val testRsaKeys: OpenIdTestKeys by lazy {
    OpenIdTestKeys.rsa(issuer = ISSUER_URL, audience = "api")
}

internal val testOtherRsaKeys: OpenIdTestKeys by lazy {
    OpenIdTestKeys.rsa(keyId = "kid-2", issuer = ISSUER_URL, audience = "api")
}

internal val testEcKeys: OpenIdTestKeys by lazy {
    OpenIdTestKeys.ec(issuer = ISSUER_URL, audience = "api")
}

internal val testRsaKeysByAlgorithm: Map<SignatureAlgorithm, OpenIdTestKeys> by lazy {
    listOf(
        SignatureAlgorithm.RSA_SHA_256,
        SignatureAlgorithm.RSA_SHA_384,
        SignatureAlgorithm.RSA_SHA_512,
    ).associateWith { algorithm ->
        OpenIdTestKeys.rsa(
            keyId = algorithm.testJwaName,
            algorithm = algorithm,
            issuer = ISSUER_URL,
            audience = "api",
        )
    }
}

internal fun jwkProviderWithMultipleKeys(vararg keys: OpenIdTestKeys): JwkProvider =
    JwkProvider { requestedKeyId ->
        if (requestedKeyId == null) {
            throw SigningKeyNotFoundException("Multiple keys are available", null)
        }
        keys.firstOrNull { it.keyId == requestedKeyId }?.jwkProvider?.get(requestedKeyId)
            ?: throw SigningKeyNotFoundException("No key found for kid $requestedKeyId", null)
    }

internal fun jwkProviderWithoutVerifyOperation(keys: OpenIdTestKeys): JwkProvider {
    val sourceJwk = keys.jwkProvider.get(keys.keyId)
    val values = sourceJwk.additionalAttributes.toMutableMap()
    values["kid"] = sourceJwk.id
    values["kty"] = sourceJwk.type
    values["alg"] = sourceJwk.algorithm
    values["use"] = sourceJwk.usage
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
