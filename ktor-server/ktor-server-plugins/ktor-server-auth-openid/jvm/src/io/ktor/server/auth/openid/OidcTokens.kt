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
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.text.equals
import kotlin.text.isNullOrBlank

internal class OidcTokenRejectedException(message: String?) : RuntimeException(message)

private fun rejectToken(message: String?): Nothing =
    throw OidcTokenRejectedException(message)

private inline fun requireToken(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        rejectToken(lazyMessage())
    }
}

private enum class JwtTokenType {
    IdToken,
    AccessToken,
    UserInfo,
}

private val hmacAlgorithms = setOf("HS256", "HS384", "HS512")
private const val BEARER_TOKEN_TYPE = "Bearer"

private val SignatureAlgorithm.ecJwaCurve: String?
    get() = when (this) {
        SignatureAlgorithm.ECDSA_SHA_256 -> "P-256"
        SignatureAlgorithm.ECDSA_SHA_384 -> "P-384"
        SignatureAlgorithm.ECDSA_SHA_512 -> "P-521"
        else -> null
    }

internal suspend fun OidcProvider<*>.buildOAuthPrincipal(
    response: OAuthAccessTokenResponse.OAuth2,
    expectedNonce: String?,
): OidcPrincipal {
    val oauthConfig = requireNotNull(config.oauthConfig) {
        "OAuth configuration is not enabled"
    }
    val idToken = response.extraParameters["id_token"]
    if (idToken != null) {
        requireBearerTokenType(response.tokenType)
        val nonce = expectedNonce ?: rejectToken("OIDC state nonce is missing")
        return buildVerifiedPrincipal(
            idToken = idToken,
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expectedAudience = oauthConfig.idTokenAudience ?: oauthConfig.clientId,
            expectedNonce = nonce,
            fetchUserInfo = oauthConfig.fetchUserInfo,
        )
    }

    requireNotNull(config.accessTokenConfig) {
        "OAuth callback did not include id_token and accessToken { audiences = ... } is not configured"
    }
    return requireNotNull(verifyAccessToken(response.accessToken)) {
        "OAuth callback access token was not accepted"
    }
}

private fun requireBearerTokenType(type: String?) {
    requireToken(type.equals(BEARER_TOKEN_TYPE, ignoreCase = true)) {
        "OIDC token response token_type must be Bearer"
    }
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
        tokenType = JwtTokenType.AccessToken,
        metadata = metadata,
        jwkProvider = jwkProvider,
    )
    val userInfo = verifiedJwt.extractUserInfoOrNull()
    return OidcPrincipal.AccessToken(
        accessToken = token,
        userInfo = userInfo,
    )
}

internal suspend fun OidcProvider<*>.buildVerifiedPrincipal(
    idToken: String,
    accessToken: String?,
    refreshToken: String?,
    expectedAudience: String,
    expectedNonce: String? = null,
    requireNonceAbsent: Boolean = false,
    fetchUserInfo: Boolean = false,
): OidcPrincipal.IdToken {
    val decoded = try {
        JWT.decode(idToken)
    } catch (cause: JWTDecodeException) {
        rejectToken(cause.message)
    }
    val metadata = currentMetadata()
    val jwkProvider = currentJwkProvider()
    val verifiedJwt = verifyJwtToken(
        token = idToken,
        jwt = decoded,
        audiences = listOf(expectedAudience),
        tokenType = JwtTokenType.IdToken,
        metadata = metadata,
        jwkProvider = jwkProvider,
    )
    val tokenNonce = verifiedJwt.getClaim("nonce").asString()
    val idTokenSubject = verifiedJwt.requireSubject()
    when {
        expectedNonce != null -> requireToken(tokenNonce == expectedNonce) {
            "ID token nonce mismatch: replay protection check failed"
        }

        requireNonceAbsent -> requireToken(tokenNonce == null) {
            "ID token nonce must not be present on refresh token response"
        }
    }
    verifiedJwt.validateAtHash(accessToken)
    val userInfoEndpoint = metadata.userInfoEndpoint
    val userInfo = if (fetchUserInfo && userInfoEndpoint != null && !accessToken.isNullOrBlank()) {
        fetchUserInfo(
            endpoint = userInfoEndpoint,
            accessToken = accessToken,
            expectedSubject = idTokenSubject,
            expectedAudience = expectedAudience,
            metadata = metadata,
            jwkProvider = jwkProvider,
        )
    } else {
        verifiedJwt.extractUserInfo()
    }

    return OidcPrincipal.IdToken(
        idToken = idToken,
        accessToken = accessToken,
        refreshToken = refreshToken,
        userInfo = userInfo,
    )
}

private suspend fun OidcProvider<*>.fetchUserInfo(
    endpoint: String,
    accessToken: String,
    expectedSubject: String,
    expectedAudience: String,
    metadata: OpenIdProviderMetadata,
    jwkProvider: JwkProvider,
): OidcPrincipal.UserInfo {
    val response = client.get(endpoint) { bearerAuth(accessToken) }
    val userInfo = if (response.contentType().isJwt()) {
        val token = response.bodyAsText()
        if (token.count { it == '.' } == 4) {
            rejectToken("Encrypted UserInfo JWT responses are not supported")
        }
        val decoded = try {
            JWT.decode(token)
        } catch (cause: JWTDecodeException) {
            rejectToken(cause.message)
        }
        verifyJwtToken(
            token = token,
            jwt = decoded,
            audiences = listOf(expectedAudience),
            tokenType = JwtTokenType.UserInfo,
            metadata = metadata,
            jwkProvider = jwkProvider,
        ).extractUserInfo()
    } else {
        response.body<OidcPrincipal.UserInfo>()
    }

    requireToken(userInfo.subject == expectedSubject) {
        "UserInfo subject mismatch: expected $expectedSubject, got ${userInfo.subject}"
    }
    return userInfo
}

private fun ContentType?.isJwt(): Boolean =
    this?.withoutParameters()?.match(ContentType("application", "jwt")) == true

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

    val allowedAlgorithms = when (tokenType) {
        JwtTokenType.IdToken ->
            config.jwtConfig.allowedAlgorithms ?: metadata.idTokenSigningAlgValuesSupported?.toSet()

        JwtTokenType.AccessToken -> config.jwtConfig.allowedAlgorithms
        JwtTokenType.UserInfo ->
            config.jwtConfig.allowedAlgorithms ?: metadata.userinfoSigningAlgValuesSupported?.toSet()
    }
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

private fun DecodedJWT.validateAtHash(accessToken: String?) {
    val actual = getClaim("at_hash").asString() ?: return
    val token = accessToken ?: rejectToken("ID token contains at_hash but access token is missing")
    val expected = calculateAtHash(input = token, jwtAlgorithm = algorithm)
    requireToken(actual == expected) {
        "ID token at_hash does not match the access token"
    }
}

private fun DecodedJWT.requireSubject(): String {
    requireToken(!subject.isNullOrBlank()) {
        "sub claim must not be blank"
    }
    return subject
}

private fun calculateAtHash(input: String, jwtAlgorithm: String): String {
    val digestAlgorithm = SignatureAlgorithm.fromJwaName(jwtAlgorithm)?.digestAlgorithm
        ?: rejectToken("Cannot validate at_hash for unsupported JWT algorithm $jwtAlgorithm")
    val digest = digestAlgorithm.toDigester().digest(input.toByteArray(Charsets.US_ASCII))
    val leftHalf = digest.copyOfRange(0, digest.size / 2)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf)
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

internal suspend fun HttpClient.discoverVerified(issuer: String): OpenIdProviderMetadata =
    fetchOpenIdMetadata(issuer).also { metadata ->
        require(metadata.issuer == issuer) {
            "OpenID issuer mismatch: expected exactly $issuer, got ${metadata.issuer}"
        }
    }
