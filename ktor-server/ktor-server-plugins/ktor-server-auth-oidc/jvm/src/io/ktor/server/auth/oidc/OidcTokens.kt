/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
private const val BEARER_TOKEN_TYPE = "Bearer"

internal suspend fun OidcProvider<*>.buildOAuthToken(
    response: OAuthAccessTokenResponse.OAuth2,
    expectedNonce: String?,
): OidcToken {
    val config = oauthConfig
    val idToken = response.extraParameters["id_token"] ?: run {
        return requireNotNull(verifyAccessToken(response.accessToken)) {
            "OAuth callback access token was not accepted"
        }
    }

    requireBearerTokenType(response.tokenType)
    val nonce = expectedNonce ?: rejectToken("OIDC state nonce is missing")
    return buildIdToken(
        idToken = idToken,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        expectedAudience = config.idTokenAudience ?: config.clientId,
        expectedNonce = nonce,
        fetchUserInfo = config.fetchUserInfo,
    )
}

private fun requireBearerTokenType(type: String?) {
    requireToken(type.equals(BEARER_TOKEN_TYPE, ignoreCase = true)) {
        "OIDC token response token_type must be Bearer"
    }
}

internal suspend fun OidcProvider<*>.verifyAccessToken(token: String): OidcToken {
    try {
        val decodeResult = runCatching { JWT.decode(token) }
        return when {
            decodeResult.isSuccess -> verifyJwtAccessToken(token, jwt = decodeResult.getOrThrow())
            canIntrospectOpaqueToken -> verifyOpaqueToken(token)
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

@OptIn(ExperimentalTime::class)
private suspend fun OidcProvider<*>.verifyOpaqueToken(token: String): OidcToken.Opaque {
    val config = accessTokenConfig
    val strategy = config.opaqueToken as OpaqueTokenStrategy.Introspect
    val introspection = client.introspectOpaqueToken(strategy, token)
    val validAudience = introspection.audience.isNotEmpty() && introspection.audience.any { candidate ->
        candidate in config.audiences
    }

    requireToken(introspection.active) { "Introspection result is not active" }
    requireToken(validAudience) { "Token audience is not valid" }
    introspection.issuer?.let { issuer ->
        val metadata = currentMetadata()
        requireToken(issuer == metadata.issuer) {
            "Token issuer is not valid: expected ${metadata.issuer}, got $issuer"
        }
    }

    val clockSkew = jwtConfig.clockSkew
    val now = Clock.System.now()
    introspection.expiresAt?.let {
        val expiresAt = Instant.fromEpochSeconds(it)
        requireToken(expiresAt + clockSkew >= now) {
            "Token is expired according to introspection result"
        }
    }
    introspection.notBefore?.let {
        val notBefore = Instant.fromEpochSeconds(it)
        requireToken(notBefore - clockSkew <= now) {
            "Token is not yet valid according to introspection result"
        }
    }

    return OidcToken.Opaque(token, introspection)
}

private suspend fun HttpClient.introspectOpaqueToken(
    strategy: OpaqueTokenStrategy.Introspect,
    token: String,
): OpaqueTokenIntrospection {
    return try {
        submitForm(
            url = strategy.endpoint,
            formParameters = Parameters.build {
                append("token", token)
                append("token_type_hint", "access_token")
                if (strategy.authMethod == OpaqueTokenIntrospectionAuthMethod.ClientSecretPost) {
                    append("client_id", strategy.clientId)
                    append("client_secret", strategy.clientSecret)
                }
            }
        ) {
            if (strategy.authMethod == OpaqueTokenIntrospectionAuthMethod.ClientSecretBasic) {
                basicAuth(username = strategy.clientId, password = strategy.clientSecret)
            }
        }.body<JsonObject>().toOpaqueTokenIntrospection()
    } catch (e: SerializationException) {
        rejectToken(e.message)
    }
}

internal suspend fun OidcProvider<*>.buildIdToken(
    idToken: String,
    accessToken: String,
    refreshToken: String?,
    expectedAudience: String,
    expectedNonce: String? = null,
    requireNonceAbsent: Boolean = false,
    fetchUserInfo: Boolean = false,
): OidcToken.Id {
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
    requireToken(!verifiedJwt.subject.isNullOrBlank()) {
        "sub claim must not be blank"
    }
    requireToken(expectedNonce == null || tokenNonce == expectedNonce) {
        "ID token nonce mismatch: replay protection check failed"
    }
    requireToken(!requireNonceAbsent || tokenNonce == null) {
        "ID token nonce must not be present on refresh token response"
    }
    verifiedJwt.validateAtHash(accessToken)

    val userInfoEndpoint = metadata.userInfoEndpoint
    val userInfo = if (fetchUserInfo && userInfoEndpoint != null && accessToken.isNotBlank()) {
        fetchUserInfo(
            endpoint = userInfoEndpoint,
            accessToken = accessToken,
            expectedSubject = verifiedJwt.subject,
            expectedAudience = oauthConfig.clientId,
            metadata = metadata,
            jwkProvider = jwkProvider,
        )
    } else {
        verifiedJwt.extractUserInfo()
    }

    return OidcToken.Id(
        value = idToken,
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
): OidcToken.UserInfo {
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
        response.body<OidcToken.UserInfo>()
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
    val jwk = try {
        withContext(Dispatchers.IO) { jwkProvider.get(keyId) }
    } catch (_: JwkException) {
        rejectToken("JWT kid $keyId does not match any JWK")
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

private fun Jwk.curveSupportsAlgorithm(algorithm: SignatureAlgorithm): Boolean {
    val expectedCurve = algorithm.ecJwaCurve ?: return true
    val curve = additionalAttributes["crv"] as? String ?: return true
    return curve == expectedCurve
}

private fun DecodedJWT.validateAtHash(accessToken: String?) {
    val actual = getClaim("at_hash").asString() ?: return
    val token = accessToken ?: rejectToken("ID token contains at_hash but access token is missing")
    val signatureAlgorithm = SignatureAlgorithm.fromJwaName(algorithm)
        ?: rejectToken("Cannot validate at_hash for unsupported JWT algorithm $algorithm")
    val expected = signatureAlgorithm.hashAccessToken(token)
    requireToken(actual == expected) {
        "ID token at_hash does not match the access token"
    }
}
