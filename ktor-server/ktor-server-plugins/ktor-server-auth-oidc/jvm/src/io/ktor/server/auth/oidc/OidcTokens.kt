/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class OidcTokenRejectedException(message: String?) : RuntimeException(message)

private fun rejectToken(message: String?): Nothing =
    throw OidcTokenRejectedException(message)

internal inline fun requireToken(condition: Boolean, lazyMessage: () -> String) {
    @OptIn(ExperimentalContracts::class)
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
private const val CLIENT_SECRET_POST = "client_secret_post"

context(state: OidcProvider.State)
internal fun OidcProvider.useBasicTokenEndpointAuth(): Boolean {
    oauthConfig.tokenEndpointAuthMethod?.let { method ->
        return method == ClientAuthenticationMethod.ClientSecretBasic
    }
    val supportedMethods = state.metadata.tokenEndpointAuthMethodsSupported ?: return false
    return CLIENT_SECRET_POST !in supportedMethods
}

context(state: OidcProvider.State)
internal suspend fun OidcProvider.refreshTokenInternal(refreshToken: String): OidcTokenRefreshResult {
    val useBasicAuth = useBasicTokenEndpointAuth()
    val url = state.metadata.tokenEndpoint
    val formParameters = Parameters.build {
        append("grant_type", "refresh_token")
        append("refresh_token", refreshToken)
        if (!useBasicAuth) {
            append("client_id", oauthConfig.clientId)
            append("client_secret", oauthConfig.clientSecret)
        }
        oauthConfig.resourceIndicators.forEach { append("resource", it) }
    }
    val response = client.submitForm(url, formParameters) {
        if (useBasicAuth) {
            basicAuth(username = oauthConfig.clientId, password = oauthConfig.clientSecret)
        }
    }.body<TokenRefreshResponse>()

    val effectiveRefreshToken = response.refreshToken ?: refreshToken
    val idToken = response.idToken?.let { token ->
        requireBearerTokenType(response.tokenType)
        buildIdToken(
            idToken = token,
            accessToken = response.accessToken,
            refreshToken = effectiveRefreshToken,
            expectedAudience = oauthConfig.clientId,
            requireNonceAbsent = true,
            fetchUserInfo = oauthConfig.fetchUserInfo,
        )
    }

    return OidcTokenRefreshResult(
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        expiresIn = response.expiresIn?.seconds,
        tokenType = response.tokenType,
        scope = response.scope,
        idToken = idToken,
    )
}

context(state: OidcProvider.State)
internal suspend fun OidcProvider.buildOAuthToken(
    response: OAuthAccessTokenResponse.OAuth2,
    expectedNonce: String?,
): OidcToken.Id {
    val idToken = response.extraParameters["id_token"]
        ?: rejectToken("OAuth callback response is missing 'id_token'")

    requireBearerTokenType(response.tokenType)
    val nonce = expectedNonce ?: rejectToken("OIDC state 'nonce' is missing")
    return buildIdToken(
        idToken = idToken,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        expectedAudience = oauthConfig.clientId,
        expectedNonce = nonce,
        fetchUserInfo = oauthConfig.fetchUserInfo,
    )
}

private fun requireBearerTokenType(type: String?) {
    requireToken(type.equals(BEARER_TOKEN_TYPE, ignoreCase = true)) {
        "OIDC token response 'token_type' must be Bearer"
    }
}

internal suspend fun OidcProvider.verifyJwtAccessToken(token: String): OidcToken.Access = withCapturedState {
    val jwt = try {
        JWT.decode(token)
    } catch (cause: JWTDecodeException) {
        rejectToken(cause.message)
    }
    val verifiedJwt = verifyJwtToken(token, jwt, bearerConfig.audience, tokenType = JwtTokenType.AccessToken)
    verifiedJwt.requireAccessTokenPurpose()
    val userInfo = verifiedJwt.takeIf { it.subject != null }?.extractUserInfo()
    return OidcToken.Access(token, userInfo)
}

@OptIn(ExperimentalTime::class)
internal suspend fun OidcProvider.introspectOpaqueToken(token: String): OidcToken.Introspected {
    val audiences = bearerConfig.audience
    val introspection = client.introspectToken(token, introspectionConfig)
    val validAudience = introspection.audience.isNotEmpty() && introspection.audience.any { candidate ->
        candidate in audiences
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

    return OidcToken.Introspected(token, introspection)
}

private suspend fun HttpClient.introspectToken(
    token: String,
    config: OidcTokenIntrospectionConfig,
): TokenIntrospection {
    val formParameters = Parameters.build {
        append("token", token)
        append("token_type_hint", "access_token")
        if (config.authMethod == ClientAuthenticationMethod.ClientSecretPost) {
            append("client_id", config.clientId)
            append("client_secret", config.clientSecret)
        }
    }
    val response = submitForm(url = config.endpoint, formParameters = formParameters) {
        if (config.authMethod == ClientAuthenticationMethod.ClientSecretBasic) {
            basicAuth(username = config.clientId, password = config.clientSecret)
        }
    }
    return response.body<JsonObject>().toTokenIntrospection()
}

context(state: OidcProvider.State)
internal suspend fun OidcProvider.buildIdToken(
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
    val verifiedJwt = verifyJwtToken(
        token = idToken,
        jwt = decoded,
        audience = setOf(expectedAudience),
        tokenType = JwtTokenType.IdToken,
    )

    // OIDC Core 2 requires exp and iat on ID tokens
    requireToken(verifiedJwt.expiresAtAsInstant != null) {
        "ID token 'exp' claim is required"
    }
    requireToken(verifiedJwt.issuedAtAsInstant != null) {
        "ID token 'iat' claim is required"
    }

    // OIDC Core 3.1.3.7 steps 4 and 5: a multi-audience token must name the authorized party,
    // and a token authorized for another client must not be accepted as this client's login
    val authorizedParty = verifiedJwt.getClaim("azp").asString()
    requireToken(verifiedJwt.audience.orEmpty().size <= 1 || authorizedParty != null) {
        "ID token with multiple audiences must contain an 'azp' claim"
    }
    requireToken(authorizedParty == null || authorizedParty == expectedAudience) {
        "ID token 'azp' claim must equal the client id $expectedAudience, got $authorizedParty"
    }

    val tokenNonce = verifiedJwt.getClaim("nonce").asString()
    requireToken(!verifiedJwt.subject.isNullOrBlank()) {
        "'sub' claim must not be blank"
    }
    requireToken(expectedNonce == null || tokenNonce == expectedNonce) {
        "ID token 'nonce' mismatch: replay protection check failed"
    }
    requireToken(!requireNonceAbsent || tokenNonce == null) {
        "ID token 'nonce' must not be present on refresh token response"
    }
    verifiedJwt.validateAtHash(accessToken)

    val userInfoEndpoint = state.metadata.userInfoEndpoint
    val userInfo = if (fetchUserInfo && userInfoEndpoint != null) {
        fetchUserInfo(
            endpoint = userInfoEndpoint,
            accessToken = accessToken,
            expectedSubject = verifiedJwt.subject,
        )
    } else {
        verifiedJwt.extractUserInfo()
    }

    return OidcToken.Id(value = idToken, accessToken, refreshToken, userInfo)
}

context(state: OidcProvider.State)
private suspend fun OidcProvider.fetchUserInfo(
    endpoint: String,
    accessToken: String,
    expectedSubject: String,
): OidcToken.UserInfo {
    val response = client.get(endpoint) {
        bearerAuth(accessToken)
    }
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
            audience = setOf(oauthConfig.clientId),
            tokenType = JwtTokenType.UserInfo,
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

// throws only OidcTokenRejectedException
context(state: OidcProvider.State)
private suspend fun OidcProvider.verifyJwtToken(
    token: String,
    jwt: DecodedJWT,
    audience: Set<String>,
    tokenType: JwtTokenType,
): DecodedJWT {
    val tokenAlgorithm = requireAllowedAlgorithm(jwt, tokenType)
    val keyId = jwt.keyId
    val jwk = try {
        withContext(Dispatchers.IO) { state.jwkProvider.get(keyId) }
    } catch (cause: JwkException) {
        rejectToken("JWT kid $keyId does not match any JWK. ${cause.message}")
    }
    requireToken(jwk.isUsableForJwsVerification(tokenAlgorithm)) {
        "JWK $keyId cannot verify JWT algorithm ${tokenAlgorithm.jwaName}"
    }

    return try {
        JWT
            .require(tokenAlgorithm.toJwtAlgorithm(jwk.publicKey))
            .withIssuer(state.metadata.issuer)
            .withAnyOfAudience(*audience.toTypedArray())
            .acceptLeeway(jwtConfig.clockSkew.inWholeSeconds)
            .build()
            .verify(token)
    } catch (cause: JWTVerificationException) {
        rejectToken(cause.message)
    }
}

private val AccessTokenUses = listOf("access_token", "access")
private val AccessTokenTypes = listOf("jwt", "at+jwt", "bearer")

private fun DecodedJWT.requireAccessTokenPurpose() {
    val tokenUse = getClaim("token_use").asString()
    requireToken(tokenUse == null || tokenUse.lowercase() in AccessTokenUses) {
        "JWT 'token_use' must be 'access_token'"
    }
    val typ = type?.lowercase()?.removePrefix("application/")
    requireToken(typ == null || typ in AccessTokenTypes) {
        "JWT 'typ' $type is not an access token"
    }
}

// throws only OidcTokenRejectedException
context(state: OidcProvider.State)
private fun OidcProvider.requireAllowedAlgorithm(
    jwt: DecodedJWT,
    tokenType: JwtTokenType,
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
            JwtTokenType.IdToken -> state.metadata.idTokenSigningAlgValuesSupported
            JwtTokenType.UserInfo -> state.metadata.userinfoSigningAlgValuesSupported
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
    val token = accessToken ?: rejectToken("ID token contains 'at_hash' but access token is missing")
    val signatureAlgorithm = SignatureAlgorithm.fromJwaName(algorithm)
        ?: rejectToken("Cannot validate 'at_hash' for unsupported JWT algorithm $algorithm")
    val expected = signatureAlgorithm.hashAccessToken(token)
    requireToken(actual == expected) {
        "ID token 'at_hash' does not match the access token"
    }
}
