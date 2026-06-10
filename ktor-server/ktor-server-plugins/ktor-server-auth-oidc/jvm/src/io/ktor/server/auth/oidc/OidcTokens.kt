/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

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
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal class OidcTokenRejectedException(message: String?) : RuntimeException(message)

private fun rejectToken(message: String?): Nothing =
    throw OidcTokenRejectedException(message)

@OptIn(ExperimentalContracts::class)
private inline fun requireToken(condition: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies condition
    }
    if (condition) return
    rejectToken(lazyMessage())
}

private enum class JwtTokenType {
    IdToken,
    AccessToken,
    UserInfo,
}

private val hmacAlgorithms = setOf("HS256", "HS384", "HS512")

internal suspend fun OidcProvider<*>.verifyAccessToken(token: String): OidcToken {
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
    } catch (cause: Exception) {
        rejectToken(cause.message)
    }
}

private suspend fun OidcProvider<*>.verifyJwtAccessToken(
    token: String,
    jwt: DecodedJWT
): OidcToken.Access {
    val verifiedJwt = verifyJwtToken(
        token = token,
        jwt = jwt,
        audiences = accessTokenConfig.audiences,
        tokenType = JwtTokenType.AccessToken,
        metadata = currentMetadata(),
        jwkProvider = currentJwkProvider(),
    )
    val userInfo = verifiedJwt.takeIf { it.subject != null }?.extractUserInfo()
    return OidcToken.Access(token, userInfo)
}

private suspend fun OidcProvider<*>.verifyJwtToken(
    token: String,
    jwt: DecodedJWT,
    audiences: Collection<String>,
    tokenType: JwtTokenType,
    metadata: OpenIdProviderMetadata,
    jwkProvider: JwkProvider,
): DecodedJWT {
    val tokenAlgorithm = requireAllowedAlgorithm(jwt, tokenType, metadata)
    val keyId = jwt.keyId
    val jwk = try {
        withContext(Dispatchers.IO) { jwkProvider.get(keyId) }
    } catch (cause: JwkException) {
        rejectToken("JWT kid $keyId does not match any JWK: ${cause.message}")
    }
    requireToken(jwk.isUsableForJwsVerification(tokenAlgorithm)) {
        "JWK $keyId cannot verify JWT algorithm ${tokenAlgorithm.jwaName}"
    }
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
            .require(tokenAlgorithm.toJwtAlgorithm(jwk.publicKey))
            .withIssuer(metadata.issuer)
            .withAnyOfAudience(*audiences.toTypedArray())
            .acceptLeeway(jwtConfig.clockSkew.inWholeSeconds)
            .build()
            .verify(token)
    } catch (cause: JWTVerificationException) {
        rejectToken(cause.message)
    }

private fun OidcProvider<*>.requireAllowedAlgorithm(
    jwt: DecodedJWT,
    tokenType: JwtTokenType,
    metadata: OpenIdProviderMetadata,
): SignatureAlgorithm {
    val algorithmName = jwt.algorithm ?: rejectToken("JWT algorithm is missing")
    requireToken(algorithmName != "none" && algorithmName !in hmacAlgorithms) {
        "JWT algorithm $algorithmName is not accepted"
    }
    val algorithm = SignatureAlgorithm.fromJwaName(algorithmName)
        ?: rejectToken("JWT algorithm $algorithmName is not accepted")

    val allowedAlgorithms = jwtConfig.allowedAlgorithms
        ?.map { algorithm -> checkNotNull(algorithm.jwaName) }
        ?: when (tokenType) {
            JwtTokenType.IdToken -> metadata.idTokenSigningAlgValuesSupported
            JwtTokenType.UserInfo -> metadata.userinfoSigningAlgValuesSupported
            JwtTokenType.AccessToken -> null
        }
    requireToken(allowedAlgorithms == null || algorithmName in allowedAlgorithms) {
        "JWT algorithm $algorithmName is not in the allowed algorithms: ${allowedAlgorithms!!.joinToString()}"
    }
    return algorithm
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

private val SignatureAlgorithm.ecJwaCurve: String?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> "P-256"
        SignatureAlgorithm.ECDSA_SHA_384 -> "P-384"
        SignatureAlgorithm.ECDSA_SHA_512 -> "P-521"
        else -> null
    }

private fun Jwk.curveSupportsAlgorithm(algorithm: SignatureAlgorithm): Boolean {
    val expectedCurve = algorithm.ecJwaCurve ?: return true
    val curve = additionalAttributes["crv"] as? String ?: return true
    return curve == expectedCurve
}

private fun SignatureAlgorithm.toJwtAlgorithm(publicKey: PublicKey): Algorithm =
    when (val publicKey = publicKey) {
        is RSAPublicKey -> when (this) {
            SignatureAlgorithm.RSA_SHA_256 -> Algorithm.RSA256(publicKey, null)
            SignatureAlgorithm.RSA_SHA_384 -> Algorithm.RSA384(publicKey, null)
            SignatureAlgorithm.RSA_SHA_512 -> Algorithm.RSA512(publicKey, null)
            else -> error("Unsupported RSA JWT signing algorithm: $name")
        }

        is ECPublicKey -> when (this) {
            SignatureAlgorithm.ECDSA_SHA_256 -> Algorithm.ECDSA256(publicKey, null)
            SignatureAlgorithm.ECDSA_SHA_384 -> Algorithm.ECDSA384(publicKey, null)
            SignatureAlgorithm.ECDSA_SHA_512 -> Algorithm.ECDSA512(publicKey, null)
            else -> error("Unsupported EC JWT signing algorithm: $name")
        }

        else -> error("Unsupported JWK key type: ${publicKey::class.simpleName}")
    }
