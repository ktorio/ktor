/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.time.Instant
import java.util.Base64

/**
 * Principal containing OpenID Connect or OAuth 2.0 token material.
 *
 * This is the base type for all token-based principals in the OpenID Connect plugin.
 * Use one of the concrete subclasses depending on the token type:
 * - [IdToken] for full OpenID Connect flows with an ID token.
 * - [AccessToken] for JWT-based access tokens (e.g., Bearer tokens).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.OidcPrincipal)
 */
public abstract class OidcPrincipal {

    /**
     * Refresh token for getting new tokens, or `null` when unavailable.
     */
    public abstract val refreshToken: String?

    /**
     * Principal from a full OpenID Connect flow containing an ID token.
     *
     * @property idToken verified ID token value.
     * @property accessToken access token returned with the ID token, or `null` when unavailable.
     * @property refreshToken refresh token returned by the token endpoint, or `null` when unavailable.
     * @property userInfo normalized user claims extracted from the ID token or userinfo endpoint.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.OidcPrincipal.IdToken)
     */
    @Serializable
    @SerialName("id_token")
    public class IdToken(
        public val idToken: String,
        public val accessToken: String? = null,
        override val refreshToken: String? = null,
        public val userInfo: UserInfo,
    ) : OidcPrincipal() {
        /**
         * Decoded claims from [idToken]. Use these values only after token verification.
         */
        public val idTokenClaims: TokenClaims by lazy { TokenClaims(JWT.decode(idToken)) }

        /**
         * Decoded claims from [accessToken], or `null` when no access token is available.
         */
        public val accessTokenClaims: TokenClaims? by lazy {
            accessToken?.let { TokenClaims(JWT.decode(it)) }
        }
    }

    /**
     * Principal from a JWT access token (e.g., a Bearer token without an accompanying ID token).
     *
     * @property accessToken verified JWT access token value.
     * @property userInfo normalized user claims extracted from the token, or `null` when unavailable.
     * @property refreshToken refresh token associated with the access token, or `null` when unavailable.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.OidcPrincipal.AccessToken)
     */
    @Serializable
    @SerialName("access_token")
    public class AccessToken(
        public val accessToken: String,
        public val userInfo: UserInfo? = null,
        override val refreshToken: String? = null,
    ) : OidcPrincipal() {
        /**
         * Decoded claims from [accessToken].
         */
        public val accessTokenClaims: TokenClaims by lazy { TokenClaims(JWT.decode(accessToken)) }

        /**
         * Client identifier from the JWT access token `client_id` claim, or `null` when absent.
         */
        public val clientId: String? get() = accessTokenClaims.claim("client_id").asString()
    }

    /**
     * Standard user claims extracted from an ID token payload or userinfo response.
     *
     * @property subject subject identifier. Must not be blank.
     * @property name display name.
     * @property email email address.
     * @property emailVerified whether the provider has verified the email address.
     * @property picture profile picture URL.
     * @property givenName given name.
     * @property familyName family name.
     * @property preferredUsername preferred username.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.OidcPrincipal.UserInfo)
     */
    @Serializable
    public class UserInfo(
        @SerialName("sub")
        public val subject: String,
        public val name: String? = null,
        public val email: String? = null,
        @SerialName("email_verified")
        public val emailVerified: Boolean? = null,
        public val picture: String? = null,
        @SerialName("given_name")
        public val givenName: String? = null,
        @SerialName("family_name")
        public val familyName: String? = null,
        @SerialName("preferred_username")
        public val preferredUsername: String? = null,
    ) {
        init {
            require(subject.isNotBlank()) { "subject must not be blank" }
        }
    }

    public companion object {
        /**
         * [SerializersModule] for polymorphic serialization of [OidcPrincipal] subclasses.
         *
         * Register this module in your [Json] configuration when using custom principal serialization.
         */
        public val serializersModule: SerializersModule by lazy {
            SerializersModule {
                polymorphic(OidcPrincipal::class) {
                    subclass(IdToken::class)
                    subclass(AccessToken::class)
                }
            }
        }
    }
}

/**
 * Structured JWT claims access.
 *
 * Claims are decoded from an already verified token by the OpenID Connect plugin. Accessing these values does not
 * perform verification by itself.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.TokenClaims)
 */
public class TokenClaims internal constructor(private val jwt: DecodedJWT) {
    /**
     * Decoded JWT header as JSON.
     */
    public val header: JsonObject get() = parseHeader(jwt.header)

    /**
     * Decoded JWT payload claims.
     */
    public val payload: Map<String, Claim> get() = jwt.claims

    /**
     * Key identifier from the JWT header.
     */
    public val keyId: String? get() = jwt.keyId

    /**
     * Type from the JWT header.
     */
    public val type: String? get() = jwt.type

    /**
     * Signing algorithm from the JWT header.
     */
    public val algorithm: String? get() = jwt.algorithm

    /**
     * Issuer claim.
     */
    public val issuer: String? get() = jwt.issuer

    /**
     * Subject claim.
     */
    public val subject: String? get() = jwt.subject

    /**
     * Audience claim values.
     */
    public val audience: List<String> get() = jwt.audience ?: emptyList()

    /**
     * Expiration time.
     */
    public val expiresAt: Instant? get() = jwt.expiresAtAsInstant

    /**
     * Not-before time.
     */
    public val notBefore: Instant? get() = jwt.notBeforeAsInstant

    /**
     * Issuance time.
     */
    public val issuedAt: Instant? get() = jwt.issuedAtAsInstant

    /**
     * JWT ID claim.
     */
    public val jwtId: String? get() = jwt.id

    /**
     * Returns a JWT claim by name.
     *
     * @param name claim name.
     * @return claim value, or a missing claim representation when absent.
     */
    public fun claim(name: String): Claim = jwt.getClaim(name)

    /**
     * Returns a JWT header value as a string.
     *
     * @param name header name.
     * @return header string value, or `null` when absent.
     */
    public fun headerString(name: String): String? = header[name]?.jsonPrimitive?.contentOrNull

    private fun parseHeader(rawHeader: String): JsonObject {
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(rawHeader)
            Json.parseToJsonElement(decoded.decodeToString()) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
    }
}

internal fun Payload.extractUserInfo(): OidcPrincipal.UserInfo {
    val subjectValue = requireNotNull(subject) {
        "subject claim is missing from the JWT payload"
    }
    require(subjectValue.isNotBlank()) {
        "subject claim must not be blank"
    }
    return OidcPrincipal.UserInfo(
        subject = subjectValue,
        name = getClaim("name").asString(),
        email = getClaim("email").asString(),
        emailVerified = getClaim("email_verified").asBoolean(),
        picture = getClaim("picture").asString(),
        givenName = getClaim("given_name").asString(),
        familyName = getClaim("family_name").asString(),
        preferredUsername = getClaim("preferred_username").asString(),
    )
}

internal fun Payload.extractUserInfoOrNull(): OidcPrincipal.UserInfo? =
    if (subject == null) null else extractUserInfo()
