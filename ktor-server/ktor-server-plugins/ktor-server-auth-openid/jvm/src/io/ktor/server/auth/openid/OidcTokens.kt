/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.auth.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

internal class OidcTokenRejectedException(message: String?) : RuntimeException(message)

private fun rejectToken(message: String?): Nothing =
    throw OidcTokenRejectedException(message)

private inline fun requireToken(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        rejectToken(lazyMessage())
    }
}

private val hmacAlgorithms = setOf("HS256", "HS384", "HS512")

private val SignatureAlgorithm.ecJwaCurve: String?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> "P-256"
        SignatureAlgorithm.ECDSA_SHA_384 -> "P-384"
        SignatureAlgorithm.ECDSA_SHA_512 -> "P-521"
        else -> null
    }

internal suspend fun OidcProvider<*>.verifyAccessToken(token: String): OidcPrincipal {
    require(config.accessTokenAllowed) {
        "Access token is not allowed"
    }
    try {
        val decodeResult = runCatching { JWT.decode(token) }
        return when {
            decodeResult.isSuccess -> verifyJwtAccessToken(token, jwt = decodeResult.getOrThrow())
            else -> rejectToken(decodeResult.exceptionOrNull()?.message)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: OidcTokenRejectedException) {
        throw cause
    } catch (cause: Throwable) {
        rejectToken(cause.message)
    }
}

private suspend fun OidcProvider<*>.verifyJwtAccessToken(
    token: String,
    jwt: DecodedJWT
): OidcPrincipal.AccessToken {
    val config = requireNotNull(config.accessTokenConfig) {
        "Access token validation is not allowed"
    }
    val metadata = currentMetadata()
    val jwkProvider = currentJwkProvider()
    val verifiedJwt = verifyJwtToken(
        token = token,
        jwt = jwt,
        audiences = config.audiences,
        metadata = metadata,
        jwkProvider = jwkProvider,
    )
    val userInfo = verifiedJwt.extractUserInfoOrNull()
    return OidcPrincipal.AccessToken(
        accessToken = token,
        userInfo = userInfo,
    )
}

private suspend fun OidcProvider<*>.verifyJwtToken(
    token: String,
    jwt: DecodedJWT,
    audiences: Collection<String>,
    metadata: OpenIdProviderMetadata,
    jwkProvider: JwkProvider,
): DecodedJWT {
    val tokenAlgorithm = requireAllowedAlgorithm(jwt)
    val keyId = jwt.keyId
    if (keyId != null) {
        val jwk = jwkProvider.getJwk(keyId)
        requireToken(jwk.isUsableForJwsVerification(tokenAlgorithm)) {
            "JWK $keyId cannot verify JWT algorithm ${tokenAlgorithm.jwaName}"
        }
        return verifyJwt(token, jwk, tokenAlgorithm, audiences, metadata)
    }

    val jwk = jwkProvider.resolveVerificationKeyForMissingKid(tokenAlgorithm)
    return verifyJwt(token, jwk, tokenAlgorithm, audiences, metadata)
}

private fun OidcProvider<*>.verifyJwt(
    token: String,
    jwk: Jwk,
    tokenAlgorithm: SignatureAlgorithm,
    audiences: Collection<String>,
    metadata: OpenIdProviderMetadata,
): DecodedJWT =
    try {
        JWT
            .require(jwk.createAlgorithm(tokenAlgorithm))
            .withIssuer(metadata.issuer)
            .withAnyOfAudience(*audiences.toTypedArray())
            .acceptLeeway(config.jwtConfig.clockSkew.inWholeSeconds)
            .build()
            .verify(token)
    } catch (cause: JWTVerificationException) {
        rejectToken(cause.message)
    }

private fun OidcProvider<*>.requireAllowedAlgorithm(jwt: DecodedJWT): SignatureAlgorithm {
    val algorithmName = jwt.algorithm ?: rejectToken("JWT algorithm is missing")
    requireToken(algorithmName != "none" && algorithmName !in hmacAlgorithms) {
        "JWT algorithm $algorithmName is not accepted"
    }
    val algorithm = SignatureAlgorithm.fromJwaName(algorithmName)
        ?: rejectToken("JWT algorithm $algorithmName is not accepted")

    val allowedAlgorithms = config.jwtConfig.allowedAlgorithms
    requireToken(allowedAlgorithms == null || algorithmName in allowedAlgorithms) {
        "JWT algorithm $algorithmName is not in the allowed algorithms: ${allowedAlgorithms!!.joinToString()}"
    }
    return algorithm
}

private suspend fun JwkProvider.getJwk(keyId: String): Jwk {
    try {
        return withContext(Dispatchers.IO) { get(keyId) }
    } catch (_: JwkException) {
        rejectToken("JWT kid $keyId does not match any JWK")
    }
}

private suspend fun JwkProvider.resolveVerificationKeyForMissingKid(
    tokenAlgorithm: SignatureAlgorithm,
): Jwk {
    val jwk = try {
        withContext(Dispatchers.IO) { get(null) }
    } catch (_: JwkException) {
        rejectToken("JWT does not contain kid and JWKS does not contain exactly one key")
    }
    requireToken(jwk.isUsableForJwsVerification(tokenAlgorithm)) {
        "JWT does not contain kid and JWKS key cannot verify JWT algorithm ${tokenAlgorithm.jwaName}"
    }
    return jwk
}

private fun Jwk.isUsableForJwsVerification(tokenAlgorithm: SignatureAlgorithm): Boolean {
    if (usage != null && usage != "sig") {
        return false
    }
    if (operationsAsList != null && "verify" !in operationsAsList) {
        return false
    }
    if (algorithm != null && algorithm != tokenAlgorithm.jwaName) {
        return false
    }
    val jwkType = tokenAlgorithm.keyAlgorithm.jwkType ?: return false
    return type == jwkType && curveSupportsAlgorithm(tokenAlgorithm)
}

private val KeyAlgorithm.jwkType: String?
    get() = when (this) {
        KeyAlgorithm.RSA -> "RSA"
        KeyAlgorithm.EC -> "EC"
        else -> null
    }

private fun Jwk.curveSupportsAlgorithm(algorithm: SignatureAlgorithm): Boolean {
    val expectedCurve = algorithm.ecJwaCurve ?: return true
    val curve = additionalAttributes["crv"] as? String ?: return true
    return curve == expectedCurve
}

private fun Jwk.createAlgorithm(tokenAlgorithm: SignatureAlgorithm): Algorithm =
    when (val publicKey = publicKey) {
        is RSAPublicKey -> when (tokenAlgorithm.jwaName) {
            SignatureAlgorithm.RSA_SHA_256.jwaName -> Algorithm.RSA256(publicKey, null)
            SignatureAlgorithm.RSA_SHA_384.jwaName -> Algorithm.RSA384(publicKey, null)
            SignatureAlgorithm.RSA_SHA_512.jwaName -> Algorithm.RSA512(publicKey, null)
            else -> error("Unsupported RSA JWT signing algorithm: ${tokenAlgorithm.jwaName}")
        }

        is ECPublicKey -> when (tokenAlgorithm.jwaName) {
            SignatureAlgorithm.ECDSA_SHA_256.jwaName -> Algorithm.ECDSA256(publicKey, null)
            SignatureAlgorithm.ECDSA_SHA_384.jwaName -> Algorithm.ECDSA384(publicKey, null)
            SignatureAlgorithm.ECDSA_SHA_512.jwaName -> Algorithm.ECDSA512(publicKey, null)
            else -> error("Unsupported EC JWT signing algorithm: ${tokenAlgorithm.jwaName}")
        }

        else -> error("Unsupported JWK key type: ${publicKey::class.simpleName}")
    }
